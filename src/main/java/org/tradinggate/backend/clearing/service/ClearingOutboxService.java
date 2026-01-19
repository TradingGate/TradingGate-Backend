package org.tradinggate.backend.clearing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.clearing.domain.ClearingBatch;
import org.tradinggate.backend.clearing.domain.ClearingResult;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchStatus;
import org.tradinggate.backend.clearing.outbox.SettlementEventBuilder;
import org.tradinggate.backend.clearing.repository.ClearingBatchRepository;
import org.tradinggate.backend.clearing.repository.ClearingResultRepository;
import org.tradinggate.backend.global.outbox.domain.OutboxProducerType;
import org.tradinggate.backend.global.outbox.infrastructure.OutboxEventRepository;
import org.tradinggate.backend.global.outbox.service.OutboxAppender;

import java.time.Instant;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
@Transactional
public class ClearingOutboxService {

    private static final int PAGE_SIZE = 1000;
    private static final int LOOKBACK_MINUTES = 180; // 최근 3시간만 보수(임시)

    private final ClearingBatchRepository clearingBatchRepository;
    private final ClearingResultRepository clearingResultRepository;
    private final SettlementEventBuilder settlementEventBuilder;
    private final OutboxAppender outboxAppender;
    private final OutboxEventRepository outboxEventRepository;
    private final ClearingOutboxRepairWorker clearingOutboxRepairWorker;

    /**
     * 배치 결과를 읽어서 Outbox에 적재한다.
     *
     * @param batchId 정산 배치 ID
     * @sideEffect outbox_event에 멱등 삽입(중복이면 무시)
     */
    public void enqueueSettlementEvents(Long batchId) {
        // 왜: 이벤트는 "저장된 결과"를 기준으로 생성해야 재실행/업서트 이후에도 정합성이 유지된다.
        ClearingBatch batch = clearingBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalStateException("ClearingBatch not found. batchId=" + batchId));

        int page = 0;
        Page<ClearingResult> results;
        do {
            results = clearingResultRepository.findByBatchId(batchId, PageRequest.of(page, PAGE_SIZE));
            if (results.isEmpty()) {
                break;
            }

            for (ClearingResult r : results.getContent()) {
                String idemKey = idempotencyKey(batchId, r.getAccountId(), r.getSymbolId());
                Map<String, Object> payload = settlementEventBuilder.build(batch, r);

                outboxAppender.append(
                        OutboxProducerType.CLEARING,
                        "CLEARING.SETTLEMENT",
                        "ClearingBatch",
                        batchId,
                        idemKey,
                        payload
                );
            }

            page++;
        } while (results.hasNext());

        // 결과는 저장됐는데 outbox가 일부만 적재되면 downstream 정합성이 깨질 수 있어, 성공 처리 전에 누락 여부를 검증한다.
        long expected = clearingResultRepository.countByBatch_Id(batchId);
        long actual = outboxEventRepository.countByIdempotencyKeyPrefix(prefix(batchId));
        if (expected != actual) {
            throw new IllegalStateException("Outbox count mismatch. batchId=" + batchId + " expected=" + expected + " actual=" + actual);
        }

    }

    /**
     * 최근 성공 배치 중 outbox 누락이 있는 배치를 찾아 복구한다.
     *
     * @return 복구 수행한 배치 수(= enqueue 호출한 배치 수)
     */
    public int repairRecentSuccessBatches() {
        Instant since = Instant.now().minusSeconds(LOOKBACK_MINUTES * 60L);

        int repaired = 0;
        int page = 0;

        while (true) {
            Page<ClearingBatch> batches = clearingBatchRepository
                    .findByStatusAndCreatedAtAfterOrderByCreatedAtAsc(ClearingBatchStatus.SUCCESS, since, PageRequest.of(page, PAGE_SIZE));

            if (batches.isEmpty()) {
                break;
            }

            for (ClearingBatch b : batches.getContent()) {
                if (needsRepair(b.getId())) {
                    try {
                        clearingOutboxRepairWorker.repairOneBatch(b.getId(), prefix(b.getId()));
                        repaired++;
                    } catch (Exception e) {
                        // 보수 작업은 best-effort이며, 한 배치 실패가 전체 보수 루프를 멈추지 않도록 한다.
                        log.warn("[CLEARING][REPAIR] failed. batchId={}, err={}", b.getId(), summarize(e));
                    }
                }
            }

            if (!batches.hasNext()) {
                break;
            }
            page++;
        }

        return repaired;
    }

    private boolean needsRepair(Long batchId) {
        long expected = clearingResultRepository.countByBatch_Id(batchId);
        long actual = outboxEventRepository.countByIdempotencyKeyPrefix(prefix(batchId));
        return expected != actual;
    }

    private String idempotencyKey(Long batchId, Long accountId, Long symbolId) {
        // 왜: "1 result -> 1 event" 규칙을 (batchId, accountId, symbolId)로 고정하여 멱등 삽입을 보장한다.
        return "clearing:settlement:" + batchId + ":" + accountId + ":" + symbolId;
    }

    private String prefix(Long batchId) {
        return "clearing:settlement:" + batchId + ":";
    }

    private String summarize(Exception e) {
        String msg = e.getMessage();
        if (msg == null) msg = "";
        String s = e.getClass().getSimpleName() + ": " + msg;
        return s.length() <= 200 ? s : s.substring(0, 200);
    }


}
