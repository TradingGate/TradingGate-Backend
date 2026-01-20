package org.tradinggate.backend.clearing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.tradinggate.backend.clearing.domain.ClearingBatch;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchStatus;
import org.tradinggate.backend.clearing.repository.ClearingBatchRepository;
import org.tradinggate.backend.clearing.repository.ClearingResultRepository;
import org.tradinggate.backend.global.outbox.infrastructure.OutboxEventRepository;

import java.time.Instant;

@Log4j2
@Service
@RequiredArgsConstructor
@Profile("clearing")
public class ClearingOutboxRepairService {

    private static final int PAGE_SIZE = 1000;
    private static final int LOOKBACK_MINUTES = 180; // 최근 3시간만 보수(임시)

    private final ClearingBatchRepository clearingBatchRepository;
    private final ClearingResultRepository clearingResultRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ClearingOutboxRepairWorker clearingOutboxRepairWorker;

    /**
     * 최근 성공 배치 중 outbox 누락이 있는 배치를 찾아 복구한다.
     *
     * @return 복구 수행한 배치 수(= repairOneBatch 호출한 배치 수)
     */
    public int repairRecentSuccessBatches() {
        Instant since = Instant.now().minusSeconds(LOOKBACK_MINUTES * 60L);

        int repaired = 0;
        int page = 0;

        while (true) {
            Page<ClearingBatch> batches = clearingBatchRepository
                    .findByStatusAndCreatedAtAfterOrderByCreatedAtAsc(
                            ClearingBatchStatus.SUCCESS,
                            since,
                            PageRequest.of(page, PAGE_SIZE)
                    );

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
