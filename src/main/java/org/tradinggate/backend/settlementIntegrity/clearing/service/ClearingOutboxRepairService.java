package org.tradinggate.backend.settlementIntegrity.clearing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.ClearingBatch;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchStatus;
import org.tradinggate.backend.settlementIntegrity.clearing.repository.ClearingBatchRepository;
import org.tradinggate.backend.settlementIntegrity.clearing.repository.ClearingResultRepository;
import org.tradinggate.backend.global.outbox.infrastructure.OutboxEventRepository;

import java.time.Instant;

@Log4j2
@Service
@RequiredArgsConstructor
@Profile("clearing")
public class ClearingOutboxRepairService {

    private static final int PAGE_SIZE = 1000;
    private static final int LOOKBACK_MINUTES = 180;

    private final ClearingBatchRepository clearingBatchRepository;
    private final ClearingResultRepository clearingResultRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ClearingOutboxRepairWorker clearingOutboxRepairWorker;

    public int repairRecentBatches() {
        Instant since = Instant.now().minusSeconds(LOOKBACK_MINUTES * 60L);

        int repaired = 0;
        repaired += repairByStatusSince(ClearingBatchStatus.SUCCESS, since);
        repaired += repairByStatusSince(ClearingBatchStatus.FAILED, since);

        return repaired;
    }

    private int repairByStatusSince(ClearingBatchStatus status, Instant since) {
        int repaired = 0;
        int page = 0;

        while (true) {
            Page<ClearingBatch> batches = clearingBatchRepository
                    .findByStatusAndCreatedAtAfterOrderByCreatedAtAsc(
                            status,
                            since,
                            PageRequest.of(page, PAGE_SIZE)
                    );

            if (batches.isEmpty()) break;

            for (ClearingBatch b : batches.getContent()) {
                if (needsRepairAndHasResults(b.getId())) {
                    try {
                        clearingOutboxRepairWorker.repairOneBatch(b.getId(), prefix(b.getId()));
                        repaired++;
                    } catch (Exception e) {
                        log.warn("[CLEARING][REPAIR] failed. batchId={}, status={}, err={}",
                                b.getId(), status, summarize(e));
                    }
                }
            }

            if (!batches.hasNext()) break;
            page++;
        }

        return repaired;
    }

    private boolean needsRepairAndHasResults(Long batchId) {
        long expected = clearingResultRepository.countByBatch_Id(batchId);
        if (expected <= 0) return false;

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
