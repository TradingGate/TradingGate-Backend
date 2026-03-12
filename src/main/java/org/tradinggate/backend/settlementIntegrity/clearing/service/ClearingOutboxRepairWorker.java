package org.tradinggate.backend.settlementIntegrity.clearing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.settlementIntegrity.clearing.repository.ClearingResultRepository;
import org.tradinggate.backend.global.outbox.infrastructure.OutboxEventRepository;

@Log4j2
@Service
@RequiredArgsConstructor
@Profile("clearing")
public class ClearingOutboxRepairWorker {

    private final ClearingResultRepository clearingResultRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ClearingOutboxService clearingOutboxService;

    /**
     * 왜: 한 배치 보수 실패가 다른 배치 보수에 영향을 주지 않도록 트랜잭션을 분리한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void repairOneBatch(Long batchId, String prefix) {
        long expected = clearingResultRepository.countByBatch_Id(batchId);
        long actual = outboxEventRepository.countByIdempotencyKeyPrefix(prefix);

        if (expected == actual) {
            return;
        }

        log.warn("[CLEARING][REPAIR] mismatch detected. batchId={}, expected={}, actual={}",
                batchId, expected, actual);

        // 멱등키 UNIQUE로 인해 중복은 무시되므로, 재호출은 '빠진 이벤트 채우기'로 동작한다.
        clearingOutboxService.enqueueSettlementEvents(batchId);

        long after = outboxEventRepository.countByIdempotencyKeyPrefix(prefix);
        if (expected != after) {
            throw new IllegalStateException("Repair not completed. batchId=" + batchId
                    + " expected=" + expected + " after=" + after);
        }

        log.info("[CLEARING][REPAIR] completed. batchId={}, expected={}", batchId, expected);
    }
}
