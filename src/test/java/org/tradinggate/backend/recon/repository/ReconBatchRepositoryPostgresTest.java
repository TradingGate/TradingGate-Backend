package org.tradinggate.backend.recon.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.settlementIntegrity.recon.domain.ReconBatch;
import org.tradinggate.backend.settlementIntegrity.recon.domain.e.ReconBatchStatus;
import org.tradinggate.backend.settlementIntegrity.recon.domain.e.ReconFailureCode;
import org.tradinggate.backend.settlementIntegrity.recon.repository.ReconBatchRepository;
import org.tradinggate.backend.support.PostgresTcBase;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ReconBatchRepositoryPostgresTest.JpaRepoTestApp.class, properties = "spring.index.ignore=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class ReconBatchRepositoryPostgresTest extends PostgresTcBase {

    @Autowired
    private ReconBatchRepository reconBatchRepository;

    @BeforeEach
    void setUp() {
        reconBatchRepository.deleteAll();
    }

    @Test
    void markSuccessWithSummary_updatesSummaryColumns() {
        ReconBatch batch = reconBatchRepository.saveAndFlush(
                ReconBatch.pending(0L, LocalDate.of(2026, 2, 23), "LIVE-2026-02-23", 1)
        );

        reconBatchRepository.tryMarkRunning(
                batch.getId(),
                ReconBatchStatus.PENDING,
                ReconBatchStatus.RUNNING,
                Instant.now()
        );

        int updated = reconBatchRepository.markSuccessWithSummary(
                batch.getId(),
                ReconBatchStatus.SUCCESS,
                Instant.now(),
                3L,
                2L,
                new BigDecimal("123.45000000")
        );

        assertThat(updated).isEqualTo(1);

        ReconBatch reloaded = reconBatchRepository.findById(batch.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ReconBatchStatus.SUCCESS);
        assertThat(reloaded.getDiffCount()).isEqualTo(3L);
        assertThat(reloaded.getHighSeverityCount()).isEqualTo(2L);
        assertThat(reloaded.getTotalAbsDiff()).isEqualByComparingTo("123.45000000");
        assertThat(reloaded.getFinishedAt()).isNotNull();
    }

    @Test
    void tryMarkRunning_returnsZero_whenBatchAlreadyRunning() {
        ReconBatch batch = reconBatchRepository.saveAndFlush(
                ReconBatch.pending(42L, LocalDate.of(2026, 2, 23), "SNAP-1", 1)
        );

        int first = reconBatchRepository.tryMarkRunning(
                batch.getId(), ReconBatchStatus.PENDING, ReconBatchStatus.RUNNING, Instant.now()
        );
        int second = reconBatchRepository.tryMarkRunning(
                batch.getId(), ReconBatchStatus.PENDING, ReconBatchStatus.RUNNING, Instant.now()
        );

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(0);
    }

    @Test
    void markSuccessWithSummary_returnsZero_whenBatchDoesNotExist() {
        int updated = reconBatchRepository.markSuccessWithSummary(
                999999L,
                ReconBatchStatus.SUCCESS,
                Instant.now(),
                1L,
                1L,
                new BigDecimal("1.0")
        );

        assertThat(updated).isEqualTo(0);
    }

    @Test
    void markFailed_updatesFailureFields() {
        ReconBatch batch = reconBatchRepository.saveAndFlush(
                ReconBatch.pending(10L, LocalDate.of(2026, 2, 23), "SNAP-FAIL", 1)
        );

        reconBatchRepository.tryMarkRunning(
                batch.getId(), ReconBatchStatus.PENDING, ReconBatchStatus.RUNNING, Instant.now()
        );

        int updated = reconBatchRepository.markFailed(
                batch.getId(),
                ReconBatchStatus.FAILED,
                Instant.now(),
                ReconFailureCode.DIFF_WRITE_FAILED,
                "diff write failed"
        );

        assertThat(updated).isEqualTo(1);
        ReconBatch reloaded = reconBatchRepository.findById(batch.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ReconBatchStatus.FAILED);
        assertThat(reloaded.getFailureCode()).isEqualTo(ReconFailureCode.DIFF_WRITE_FAILED);
        assertThat(reloaded.getRemark()).isEqualTo("diff write failed");
        assertThat(reloaded.getFinishedAt()).isNotNull();
    }

    @Test
    void findTopByClearingBatchIdOrderByAttemptDesc_returnsLatestAttempt() {
        reconBatchRepository.saveAndFlush(ReconBatch.pending(77L, LocalDate.of(2026, 2, 23), "SNAP-A", 1));
        ReconBatch latest = reconBatchRepository.saveAndFlush(ReconBatch.pending(77L, LocalDate.of(2026, 2, 23), "SNAP-A", 3));
        reconBatchRepository.saveAndFlush(ReconBatch.pending(77L, LocalDate.of(2026, 2, 23), "SNAP-A", 2));
        reconBatchRepository.saveAndFlush(ReconBatch.pending(88L, LocalDate.of(2026, 2, 23), "SNAP-B", 9));

        ReconBatch found = reconBatchRepository.findTopByClearingBatchIdOrderByAttemptDesc(77L).orElseThrow();

        assertThat(found.getId()).isEqualTo(latest.getId());
        assertThat(found.getAttempt()).isEqualTo(3);
    }

    @Test
    void findByClearingBatchIdAndAttempt_returnsExactAttempt() {
        reconBatchRepository.saveAndFlush(ReconBatch.pending(91L, LocalDate.of(2026, 2, 23), "SNAP-91", 1));
        ReconBatch target = reconBatchRepository.saveAndFlush(ReconBatch.pending(91L, LocalDate.of(2026, 2, 23), "SNAP-91", 2));

        ReconBatch found = reconBatchRepository.findByClearingBatchIdAndAttempt(91L, 2).orElseThrow();

        assertThat(found.getId()).isEqualTo(target.getId());
        assertThat(found.getAttempt()).isEqualTo(2);
    }

    @Test
    void findStatusById_returnsCurrentStatus_afterTransition() {
        ReconBatch batch = reconBatchRepository.saveAndFlush(
                ReconBatch.pending(0L, LocalDate.of(2026, 2, 23), "LIVE-2026-02-23", 1)
        );

        reconBatchRepository.tryMarkRunning(batch.getId(), ReconBatchStatus.PENDING, ReconBatchStatus.RUNNING, Instant.now());

        assertThat(reconBatchRepository.findStatusById(batch.getId()))
                .contains(ReconBatchStatus.RUNNING);
    }

    @Test
    void findTopByClearingBatchIdAndBusinessDateOrderByAttemptDesc_scopesByBusinessDate() {
        reconBatchRepository.saveAndFlush(ReconBatch.pending(0L, LocalDate.of(2026, 2, 23), "LIVE-2026-02-23", 3));
        reconBatchRepository.saveAndFlush(ReconBatch.pending(0L, LocalDate.of(2026, 2, 24), "LIVE-2026-02-24", 4));
        ReconBatch target = reconBatchRepository.saveAndFlush(ReconBatch.pending(0L, LocalDate.of(2026, 2, 24), "LIVE-2026-02-24", 8));

        ReconBatch found = reconBatchRepository
                .findTopByClearingBatchIdAndBusinessDateOrderByAttemptDesc(0L, LocalDate.of(2026, 2, 24))
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(target.getId());
        assertThat(found.getAttempt()).isEqualTo(8);
        assertThat(found.getBusinessDate()).isEqualTo(LocalDate.of(2026, 2, 24));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "org.redisson.spring.starter.RedissonAutoConfigurationV2"
    })
    @EntityScan(basePackageClasses = {
            ReconBatch.class
    })
    @EnableJpaRepositories(basePackageClasses = {
            ReconBatchRepository.class
    })
    static class JpaRepoTestApp {
    }
}
