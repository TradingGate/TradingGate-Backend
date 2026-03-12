package org.tradinggate.backend.settlementIntegrity.clearing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.ClearingBatch;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.ClearingResult;
import org.tradinggate.backend.settlementIntegrity.clearing.outbox.SettlementEventBuilder;
import org.tradinggate.backend.settlementIntegrity.clearing.repository.ClearingBatchRepository;
import org.tradinggate.backend.settlementIntegrity.clearing.repository.ClearingResultRepository;
import org.tradinggate.backend.global.outbox.domain.OutboxProducerType;
import org.tradinggate.backend.global.outbox.infrastructure.OutboxEventRepository;
import org.tradinggate.backend.global.outbox.service.OutboxAppender;

import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
@Transactional
@Profile("clearing")
public class ClearingOutboxService {

    private static final int PAGE_SIZE = 1000;
    private static final Sort OUTBOX_PAGE_SORT = Sort.by(Sort.Direction.ASC, "id");

    private final ClearingBatchRepository clearingBatchRepository;
    private final ClearingResultRepository clearingResultRepository;
    private final SettlementEventBuilder settlementEventBuilder;
    private final OutboxAppender outboxAppender;
    private final OutboxEventRepository outboxEventRepository;

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
            // 배치 결과를 페이지로 나눠 읽을 때 정렬이 없으면 대량 row에서 일부 누락/중복이 생길 수 있다.
            results = clearingResultRepository.findByBatchId(batchId, PageRequest.of(page, PAGE_SIZE, OUTBOX_PAGE_SORT));
            if (results.isEmpty()) {
                break;
            }

            for (ClearingResult r : results.getContent()) {
                String idemKey = idempotencyKey(batchId, r.getAccountId(), r.getAsset());
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

    private String idempotencyKey(Long batchId, Long accountId, String asset) {
        return "clearing:settlement:" + batchId + ":" + accountId + ":" + asset;
    }

    private String prefix(Long batchId) {
        return "clearing:settlement:" + batchId + ":";
    }
}
