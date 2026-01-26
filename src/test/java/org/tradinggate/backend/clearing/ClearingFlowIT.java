package org.tradinggate.backend.clearing;
import java.math.BigDecimal;
import java.util.Map;
import org.tradinggate.backend.clearing.dto.ClearingResultRow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.tradinggate.backend.clearing.domain.ClearingBatch;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchStatus;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.clearing.dto.ClearingComputationContext;
import org.tradinggate.backend.clearing.policy.ScheduledClearingBatchTriggerPolicy;
import org.tradinggate.backend.clearing.repository.ClearingBatchRepository;
import org.tradinggate.backend.clearing.repository.ClearingResultRepository;
import org.tradinggate.backend.clearing.service.ClearingBatchRunner;
import org.tradinggate.backend.clearing.service.ClearingBatchService;
import org.tradinggate.backend.clearing.service.ClearingOutboxRepairService;
import org.tradinggate.backend.clearing.service.ClearingOutboxService;
import org.tradinggate.backend.clearing.service.port.ClearingBatchContextProvider;
import org.tradinggate.backend.clearing.service.port.ClearingCalculator;
import org.tradinggate.backend.global.outbox.domain.OutboxEvent;
import org.tradinggate.backend.global.outbox.domain.OutboxProducerType;
import org.tradinggate.backend.global.outbox.domain.OutboxStatus;
import org.tradinggate.backend.global.outbox.infrastructure.KafkaOutboxMessageSender;
import org.tradinggate.backend.global.outbox.infrastructure.OutboxEventRepository;
import org.tradinggate.backend.global.outbox.service.OutboxPublisher;
import org.tradinggate.backend.support.PostgresTcBase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration," +
                "org.redisson.spring.starter.RedissonAutoConfigurationV2"
})
@ActiveProfiles("clearing")
class ClearingFlowIT extends PostgresTcBase {

    @Autowired
    ClearingBatchRepository clearingBatchRepository;
    @Autowired
    ClearingResultRepository clearingResultRepository;
    @Autowired
    OutboxEventRepository outboxEventRepository;
    @Autowired
    ClearingOutboxService clearingOutboxService;

    @Autowired
    ClearingOutboxRepairService clearingOutboxRepairService;

    @MockBean
    ClearingCalculator clearingCalculator;

    @MockBean
    ClearingBatchContextProvider provider;

    @MockBean
    KafkaOutboxMessageSender outboxMessageSender;

    @Autowired
    OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() throws Exception {
        // 기본은 성공 플로우로 둔다. (개별 테스트에서 필요한 경우에만 예외를 오버라이드)
        given(provider.resolve(any(LocalDate.class), any(ClearingBatchType.class), any(String.class)))
                .willReturn(new ClearingBatchContextProvider.ClearingBatchContext(
                        Map.of("0", 10L, "1", 20L, "2", 30L),
                        20260119L
                ));

        given(clearingCalculator.calculate(any(ClearingComputationContext.class)))
                .willReturn(sampleRows());

        // send(...) is declared with `throws Exception`, so use doAnswer/doThrow style stubbing.
        Mockito.doAnswer(invocation -> null)
                .when(outboxMessageSender)
                .send(any());
    }

    /**
     * 아래 Runner/Facade 빈은 프로젝트 실제 클래스명으로 바꿔서 주입하면 됨.
     * 예: ClearingBatchRunner / ClearingRunner / ClearingFacade 등
     */
    @Autowired
    ClearingBatchRunner clearingBatchRunner;

    @Autowired
    ClearingBatchService clearingBatchService;

    @Autowired
    ScheduledClearingBatchTriggerPolicy scheduledClearingBatchTriggerPolicy;

    /*@Test
    void e2e_runOnce_createsResultsAndOutbox_and_marksSuccess() {
        // given
        LocalDate businessDate = LocalDate.of(2026, 1, 19);
        ClearingBatchType batchType = ClearingBatchType.EOD;
        String scope = ""; // ALL

        // when
        clearingBatchRunner.run(businessDate, batchType, scope, scheduledClearingBatchTriggerPolicy);

        // then - 최신 배치 조회(새 배치 생성 정책에도 안전)
        ClearingBatch batch = clearingBatchRepository
                .findTopByBusinessDateAndBatchTypeAndScopeOrderByIdDesc(businessDate, batchType, scope)
                .orElseThrow();

        Long batchId = batch.getId();

        assertThat(batch.getStatus()).isEqualTo(ClearingBatchStatus.SUCCESS);
        assertThat(batch.getCutoffOffsets()).isNotNull();
        assertThat(batch.getMarketSnapshotId()).isNotNull();

        long resultCount = clearingResultRepository.countByBatch_Id(batchId);
        assertThat(resultCount).isGreaterThan(0);

        String prefix = "clearing:settlement:" + batchId + ":";
        long outboxCount = outboxEventRepository.countByIdempotencyKeyPrefix(prefix);
        assertThat(outboxCount).isEqualTo(resultCount);
    }*/

    @Test
    void e2e_enqueueSettlementEvents_isIdempotent() {
        // given
        LocalDate businessDate = LocalDate.of(2026, 1, 19);
        ClearingBatchType batchType = ClearingBatchType.EOD;
        String scope = "";

        ClearingBatch pre = clearingBatchService.getOrCreatePending(businessDate, batchType, scope);
        Long batchId = pre.getId();
        clearingBatchRunner.run(businessDate, batchType, scope, scheduledClearingBatchTriggerPolicy);

        String prefix = "clearing:settlement:" + batchId + ":";
        long before = outboxEventRepository.countByIdempotencyKeyPrefix(prefix);

        // when: 같은 batch에 대해 enqueue를 2번 호출
        clearingOutboxService.enqueueSettlementEvents(batchId);
        clearingOutboxService.enqueueSettlementEvents(batchId);

        // then: outbox row 증가 없음(멱등)
        long after = outboxEventRepository.countByIdempotencyKeyPrefix(prefix);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void e2e_enqueueSettlementEvents_isIdempotent_underConcurrency() throws Exception {
        // given
        LocalDate businessDate = LocalDate.of(2026, 1, 19);
        ClearingBatchType batchType = ClearingBatchType.EOD;
        String scope = "";

        ClearingBatch pre = clearingBatchService.getOrCreatePending(businessDate, batchType, scope);
        Long batchId = pre.getId();
        clearingBatchRunner.run(businessDate, batchType, scope, scheduledClearingBatchTriggerPolicy);

        String prefix = "clearing:settlement:" + batchId + ":";
        long before = outboxEventRepository.countByIdempotencyKeyPrefix(prefix);

        // when: 같은 batch에 대해 enqueue를 동시에 2번 호출
        runConcurrently(
                List.of(
                        () -> {
                            clearingOutboxService.enqueueSettlementEvents(batchId);
                            return null;
                        },
                        () -> {
                            clearingOutboxService.enqueueSettlementEvents(batchId);
                            return null;
                        }
                )
        );

        // then: outbox row 증가 없음(멱등)
        long after = outboxEventRepository.countByIdempotencyKeyPrefix(prefix);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void e2e_repairRecentSuccessBatches_isBestEffort_andDoesNotThrow() {
        // given
        LocalDate businessDate = LocalDate.of(2026, 1, 19);
        ClearingBatchType batchType = ClearingBatchType.EOD;
        String scope = "";

        clearingBatchRunner.run(businessDate, batchType, scope, scheduledClearingBatchTriggerPolicy);

        // when
        int repaired = clearingOutboxRepairService.repairRecentSuccessBatches();

        // then
        assertThat(repaired).isGreaterThanOrEqualTo(0);
    }

    private void runConcurrently(List<Callable<Void>> tasks) throws InterruptedException, ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(pool.submit(task));
            }
            for (Future<Void> f : futures) {
                f.get(); // propagate exceptions
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private List<ClearingResultRow> sampleRows() {
        return List.of(
                new ClearingResultRow(
                        1001L,
                        1L,
                        BigDecimal.ZERO,
                        BigDecimal.ONE,
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(110),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                ),
                new ClearingResultRow(
                        1001L,
                        2L,
                        BigDecimal.ZERO,
                        BigDecimal.ONE,
                        BigDecimal.valueOf(200),
                        BigDecimal.valueOf(210),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                )
        );
    }

    @Test
    void e2e_rerun_afterSuccess_doesNotCreateMoreResultsOrOutbox() {
        LocalDate businessDate = LocalDate.of(2026, 1, 19);
        ClearingBatchType batchType = ClearingBatchType.EOD;
        String scope = "";

        clearingBatchRunner.run(businessDate, batchType, scope, scheduledClearingBatchTriggerPolicy);

        ClearingBatch batch = clearingBatchRepository
                .findTopByBusinessDateAndBatchTypeAndScopeOrderByIdDesc(businessDate, batchType, scope)
                .orElseThrow();
        Long batchId = batch.getId();

        long resultsBefore = clearingResultRepository.countByBatch_Id(batchId);
        String prefix = "clearing:settlement:" + batchId + ":";
        long outboxBefore = outboxEventRepository.countByIdempotencyKeyPrefix(prefix);

        // 같은 정책으로 한 번 더
        clearingBatchRunner.run(businessDate, batchType, scope, scheduledClearingBatchTriggerPolicy);

        long resultsAfter = clearingResultRepository.countByBatch_Id(batchId);
        long outboxAfter = outboxEventRepository.countByIdempotencyKeyPrefix(prefix);

        assertThat(resultsAfter).isEqualTo(resultsBefore);
        assertThat(outboxAfter).isEqualTo(outboxBefore);
    }

    @Test
    void e2e_whenNotAcquired_resultsAndOutboxAreNotCreated() {
        LocalDate businessDate = LocalDate.of(2026, 1, 19);
        ClearingBatchType batchType = ClearingBatchType.EOD;
        String scope = "";

        // 1차 실행으로 성공 배치 하나 만들고
        clearingBatchRunner.run(businessDate, batchType, scope, scheduledClearingBatchTriggerPolicy);
        ClearingBatch batch = clearingBatchRepository
                .findTopByBusinessDateAndBatchTypeAndScopeOrderByIdDesc(businessDate, batchType, scope)
                .orElseThrow();

        Long batchId = batch.getId();
        long resultsBefore = clearingResultRepository.countByBatch_Id(batchId);
        String prefix = "clearing:settlement:" + batchId + ":";
        long outboxBefore = outboxEventRepository.countByIdempotencyKeyPrefix(prefix);

        // 2차 실행(보통 정책 SKIP/REJECT로 빠질 것)
        clearingBatchRunner.run(businessDate, batchType, scope, scheduledClearingBatchTriggerPolicy);

        long resultsAfter = clearingResultRepository.countByBatch_Id(batchId);
        long outboxAfter = outboxEventRepository.countByIdempotencyKeyPrefix(prefix);

        assertThat(resultsAfter).isEqualTo(resultsBefore);
        assertThat(outboxAfter).isEqualTo(outboxBefore);
    }

    @Test
    void e2e_whenProviderResolveFails_batchIsMarkedFailed() {
        LocalDate businessDate = LocalDate.of(2026, 1, 19);
        ClearingBatchType batchType = ClearingBatchType.EOD;
        String scope = "";

        // provider가 예외를 던지게
        given(provider.resolve(businessDate, batchType, scope))
                .willThrow(new RuntimeException("boom"));

        // run
        try {
            clearingBatchRunner.run(businessDate, batchType, scope, scheduledClearingBatchTriggerPolicy);
        } catch (Exception ignored) {
            // runner는 throw 하니까 예외는 무시
        }

        ClearingBatch batch = clearingBatchRepository
                .findTopByBusinessDateAndBatchTypeAndScopeOrderByIdDesc(businessDate, batchType, scope)
                .orElseThrow();

        assertThat(batch.getStatus()).isEqualTo(ClearingBatchStatus.FAILED);
        // remark/failureCode 컬럼이 있다면 여기서 같이 검증 추천
    }

    @Test
    void e2e_whenCalculatorFails_batchIsMarkedFailed() {
        LocalDate businessDate = LocalDate.of(2026, 1, 19);
        ClearingBatchType batchType = ClearingBatchType.EOD;
        String scope = "";


        given(clearingCalculator.calculate(any(ClearingComputationContext.class)))
                .willThrow(new RuntimeException("calc boom"));

        try {
            clearingBatchRunner.run(businessDate, batchType, scope, scheduledClearingBatchTriggerPolicy);
        } catch (Exception ignored) {}

        ClearingBatch batch = clearingBatchRepository
                .findTopByBusinessDateAndBatchTypeAndScopeOrderByIdDesc(businessDate, batchType, scope)
                .orElseThrow();

        assertThat(batch.getStatus()).isEqualTo(ClearingBatchStatus.FAILED);
    }

    @Test
    void e2e_successBatch_withScheduledPolicy_isSkipped_andHasNoSideEffects() {
        LocalDate businessDate = LocalDate.of(2026, 1, 19);
        ClearingBatchType batchType = ClearingBatchType.EOD;
        String scope = "";

        // First run
        clearingBatchRunner.run(businessDate, batchType, scope, scheduledClearingBatchTriggerPolicy);

        ClearingBatch batch = clearingBatchRepository
                .findTopByBusinessDateAndBatchTypeAndScopeOrderByIdDesc(businessDate, batchType, scope)
                .orElseThrow();
        Long batchId = batch.getId();

        long resultsBefore = clearingResultRepository.countByBatch_Id(batchId);
        String prefix = "clearing:settlement:" + batchId + ":";
        long outboxBefore = outboxEventRepository.countByIdempotencyKeyPrefix(prefix);

        // Clear invocations so we can verify no interactions later
        Mockito.clearInvocations(provider, clearingCalculator);

        // Second run with scheduled policy should skip and do nothing
        clearingBatchRunner.run(businessDate, batchType, scope, scheduledClearingBatchTriggerPolicy);

        long resultsAfter = clearingResultRepository.countByBatch_Id(batchId);
        long outboxAfter = outboxEventRepository.countByIdempotencyKeyPrefix(prefix);

        assertThat(resultsAfter).isEqualTo(resultsBefore);
        assertThat(outboxAfter).isEqualTo(outboxBefore);

        Mockito.verifyNoInteractions(provider, clearingCalculator);
    }

    @Test
    void e2e_outboxPublisher_publishOnce_isSafeUnderConcurrency() throws Exception {
        // given
        int total = 50;
        for (int i = 0; i < total; i++) {
            String idempotencyKey = "test:pub:" + i;
            String payload = "{\"topic\":\"test.topic\",\"key\":\"k\",\"body\":{}}";
            outboxEventRepository.insertIgnoreConflict(
                    OutboxProducerType.CLEARING.name(),
                    "TEST.EVENT",
                    "TestAggregate",
                    1L,
                    idempotencyKey,
                    payload,
                    OutboxStatus.PENDING.name()
            );
        }

        // run concurrently two publishOnce calls
        runConcurrently(List.of(
                () -> { outboxPublisher.publishOnce(); return null; },
                () -> { outboxPublisher.publishOnce(); return null; }
        ));

        // then all events should be SENT (no PENDING)
        for (int i = 0; i < total; i++) {
            String idempotencyKey = "test:pub:" + i;
            OutboxEvent event = outboxEventRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
            assertThat(event.getRetryCount()).isZero();
        }
    }

    @Test
    void e2e_outboxPublisher_retryAndFailPolicy_works() throws Exception {
        String idempotencyKey = "test:retry:1";
        String payload = "{\"topic\":\"test.topic\",\"key\":\"k\",\"body\":{}}";
        outboxEventRepository.insertIgnoreConflict(
                OutboxProducerType.CLEARING.name(),
                "TEST.EVENT",
                "TestAggregate",
                1L,
                idempotencyKey,
                payload,
                OutboxStatus.PENDING.name()
        );

        // Stub send to throw exception to simulate failure
        Mockito.doThrow(new RuntimeException("send boom"))
                .when(outboxMessageSender)
                .send(any(OutboxEvent.class));

        // First publishOnce call increments retryCount
        outboxPublisher.publishOnce();

        OutboxEvent event = outboxEventRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getLastError()).isNotNull();

        // Retry multiple times to exceed maxRetries (default 10)
        int maxRetries = 10;
        for (int i = 0; i < maxRetries + 5; i++) {
            outboxPublisher.publishOnce();
        }

        event = outboxEventRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(event.getRetryCount()).isGreaterThanOrEqualTo(maxRetries);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    void e2e_nfrC01_referencePoints_arePersistedOnAcquire() {
        LocalDate businessDate = LocalDate.of(2026, 1, 19);
        ClearingBatchType batchType = ClearingBatchType.EOD;
        String scope = "";

        clearingBatchRunner.run(businessDate, batchType, scope, scheduledClearingBatchTriggerPolicy);

        ClearingBatch batch = clearingBatchRepository
                .findTopByBusinessDateAndBatchTypeAndScopeOrderByIdDesc(businessDate, batchType, scope)
                .orElseThrow();

        assertThat(batch.getCutoffOffsets()).isEqualTo(Map.of("0", 10L, "1", 20L, "2", 30L));
        assertThat(batch.getMarketSnapshotId()).isEqualTo(20260119L);
    }
}
