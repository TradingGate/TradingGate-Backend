package org.tradinggate.backend.recon.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.ClearingBatch;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.settlementIntegrity.clearing.repository.ClearingBatchRepository;
import org.tradinggate.backend.settlementIntegrity.recon.domain.ReconBatch;
import org.tradinggate.backend.settlementIntegrity.recon.domain.ReconDiff;
import org.tradinggate.backend.settlementIntegrity.recon.domain.e.ReconBatchStatus;
import org.tradinggate.backend.settlementIntegrity.recon.repository.ReconBatchRepository;
import org.tradinggate.backend.settlementIntegrity.recon.repository.ReconDiffRepository;
import org.tradinggate.backend.settlementIntegrity.recon.service.DbReconInputsPort;
import org.tradinggate.backend.settlementIntegrity.recon.service.ReconBatchRunner;
import org.tradinggate.backend.settlementIntegrity.recon.service.ReconBatchService;
import org.tradinggate.backend.settlementIntegrity.recon.service.ReconDiffWriter;
import org.tradinggate.backend.settlementIntegrity.recon.service.support.ReconComparator;
import org.tradinggate.backend.settlementIntegrity.recon.service.support.ReconPrecisionPolicy;
import org.tradinggate.backend.risk.domain.entity.balance.AccountBalance;
import org.tradinggate.backend.risk.domain.entity.ledger.EntryType;
import org.tradinggate.backend.risk.domain.entity.ledger.LedgerEntry;
import org.tradinggate.backend.risk.repository.balance.AccountBalanceRepository;
import org.tradinggate.backend.risk.repository.ledger.LedgerEntryRepository;
import org.tradinggate.backend.support.PostgresTcBase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = ReconBatchRunnerIntegrationTest.ReconIntegrationTestApp.class,
        properties = "spring.index.ignore=true"
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({"test", "clearing"})
class ReconBatchRunnerIntegrationTest extends PostgresTcBase {

    @Autowired
    private ReconBatchRunner reconBatchRunner;

    @Autowired
    private ReconBatchRepository reconBatchRepository;

    @Autowired
    private ReconDiffRepository reconDiffRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private AccountBalanceRepository accountBalanceRepository;

    @MockBean
    private ClearingBatchRepository clearingBatchRepository;

    @BeforeEach
    void setUp() {
        reconDiffRepository.deleteAll();
        reconBatchRepository.deleteAll();
        accountBalanceRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
    }

    @Test
    void runStandalone_createsDiffsAndSummary_andMarksSuccess() {
        LocalDate businessDate = LocalDate.of(2026, 2, 23);

        // truth (ledger)
        ledgerEntryRepository.save(entry(1001L, "USDT", "100.000000001", EntryType.TRADE, "TR-1", businessDate.atTime(10, 0)));
        ledgerEntryRepository.save(entry(1002L, "KRW", "5000", EntryType.TRADE, "TR-2", businessDate.atTime(10, 1)));

        // snapshot (projection)
        accountBalanceRepository.save(balance(1001L, "usdt", "95.000000001", "0"));
        accountBalanceRepository.save(balance(1002L, "KRW", "5000", "0")); // exact match -> no diff

        reconBatchRunner.runStandalone(businessDate);

        ReconBatch batch = reconBatchRepository.findTopByClearingBatchIdOrderByAttemptDesc(0L).orElseThrow();
        assertThat(batch.getStatus()).isEqualTo(ReconBatchStatus.SUCCESS);
        assertThat(batch.getAttempt()).isEqualTo(1);
        assertThat(batch.getDiffCount()).isEqualTo(1L);
        assertThat(batch.getHighSeverityCount()).isEqualTo(1L);
        assertThat(batch.getTotalAbsDiff()).isEqualByComparingTo("5.00000000");

        List<ReconDiff> diffs = reconDiffRepository.findAll();
        assertThat(diffs).hasSize(1);
        ReconDiff diff = diffs.get(0);
        assertThat(diff.getReconBatchId()).isEqualTo(batch.getId());
        assertThat(diff.getAccountId()).isEqualTo(1001L);
        assertThat(diff.getAsset()).isEqualTo("USDT");
        assertThat(diff.getExpectedValue()).isEqualByComparingTo("100.00000000");
        assertThat(diff.getActualValue()).isEqualByComparingTo("95.00000000");
        assertThat(diff.getDiffValue()).isEqualByComparingTo("-5.00000000");
    }

    @Test
    void runStandalone_doesNotReacquire_whenLatestStandaloneBatchAlreadySucceeded() {
        LocalDate businessDate = LocalDate.of(2026, 2, 24);

        ledgerEntryRepository.save(entry(2001L, "USDT", "10", EntryType.TRADE, "TR-A", businessDate.atTime(9, 0)));
        accountBalanceRepository.save(balance(2001L, "USDT", "7", "0")); // first run mismatch

        reconBatchRunner.runStandalone(businessDate);

        ReconBatch first = reconBatchRepository.findTopByClearingBatchIdOrderByAttemptDesc(0L).orElseThrow();
        assertThat(first.getAttempt()).isEqualTo(1);
        assertThat(reconDiffRepository.countByReconBatchId(first.getId())).isEqualTo(1L);
        assertThat(first.getDiffCount()).isEqualTo(1L);

        // fix projection and rerun -> current runner semantics: latest SUCCESS batch is not reacquired, so no rewrite occurs
        accountBalanceRepository.deleteAll();
        accountBalanceRepository.save(balance(2001L, "USDT", "10", "0"));

        reconBatchRunner.runStandalone(businessDate);

        ReconBatch second = reconBatchRepository.findTopByClearingBatchIdOrderByAttemptDesc(0L).orElseThrow();
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getAttempt()).isEqualTo(1);
        assertThat(second.getStatus()).isEqualTo(ReconBatchStatus.SUCCESS);
        assertThat(second.getDiffCount()).isEqualTo(1L);
        assertThat(second.getHighSeverityCount()).isEqualTo(1L);
        assertThat(second.getTotalAbsDiff()).isEqualByComparingTo("3.00000000");
        assertThat(reconDiffRepository.countByReconBatchId(second.getId())).isEqualTo(1L);
    }

    @Test
    void rerunStandaloneNewAttempt_createsNewAttempt_andUsesCurrentSnapshot() {
        LocalDate businessDate = LocalDate.of(2026, 2, 25);

        ledgerEntryRepository.save(entry(3001L, "USDT", "10", EntryType.TRADE, "TR-R1", businessDate.atTime(9, 0)));
        accountBalanceRepository.save(balance(3001L, "USDT", "7", "0"));

        reconBatchRunner.runStandalone(businessDate);

        ReconBatch first = reconBatchRepository
                .findTopByClearingBatchIdAndBusinessDateOrderByAttemptDesc(0L, businessDate)
                .orElseThrow();
        assertThat(first.getDiffCount()).isEqualTo(1L);
        assertThat(reconDiffRepository.countByReconBatchId(first.getId())).isEqualTo(1L);

        accountBalanceRepository.deleteAll();
        accountBalanceRepository.save(balance(3001L, "USDT", "10", "0"));

        reconBatchRunner.rerunStandaloneNewAttempt(businessDate);

        ReconBatch latestForDate = reconBatchRepository
                .findTopByClearingBatchIdAndBusinessDateOrderByAttemptDesc(0L, businessDate)
                .orElseThrow();

        assertThat(latestForDate.getId()).isNotEqualTo(first.getId());
        assertThat(latestForDate.getAttempt()).isGreaterThan(first.getAttempt());
        assertThat(latestForDate.getRetryOfBatchId()).isEqualTo(first.getId());
        assertThat(latestForDate.getStatus()).isEqualTo(ReconBatchStatus.SUCCESS);
        assertThat(latestForDate.getDiffCount()).isEqualTo(0L);
        assertThat(latestForDate.getHighSeverityCount()).isEqualTo(0L);
        assertThat(latestForDate.getTotalAbsDiff()).isEqualByComparingTo("0");
        assertThat(reconDiffRepository.countByReconBatchId(latestForDate.getId())).isEqualTo(0L);

        // previous attempt kept for auditability
        assertThat(reconDiffRepository.countByReconBatchId(first.getId())).isEqualTo(1L);
    }

    @Test
    void runStandalone_precisionNormalization_preventsFalsePositiveDiff() {
        LocalDate businessDate = LocalDate.of(2026, 2, 26);

        // USDT scale=8: values differ at 9th decimal only -> no diff after normalization
        ledgerEntryRepository.save(entry(4001L, "USDT", "10.123456784", EntryType.TRADE, "P-USDT", businessDate.atTime(9, 0)));
        accountBalanceRepository.save(balance(4001L, "usdt", "10.123456783", "0"));

        // KRW scale=0: rounded values should match (100.4 -> 100, snapshot=100)
        ledgerEntryRepository.save(entry(4002L, "KRW", "100.4", EntryType.TRADE, "P-KRW", businessDate.atTime(9, 1)));
        accountBalanceRepository.save(balance(4002L, "KRW", "100", "0"));

        reconBatchRunner.runStandalone(businessDate);

        ReconBatch batch = reconBatchRepository
                .findTopByClearingBatchIdAndBusinessDateOrderByAttemptDesc(0L, businessDate)
                .orElseThrow();

        assertThat(batch.getStatus()).isEqualTo(ReconBatchStatus.SUCCESS);
        assertThat(batch.getDiffCount()).isEqualTo(0L);
        assertThat(batch.getHighSeverityCount()).isEqualTo(0L);
        assertThat(batch.getTotalAbsDiff()).isEqualByComparingTo("0");
        assertThat(reconDiffRepository.countByReconBatchId(batch.getId())).isEqualTo(0L);
    }

    @Test
    void runStandalone_differentBusinessDates_createSeparatedStandaloneBatches() {
        LocalDate d1 = LocalDate.of(2026, 2, 27);
        LocalDate d2 = LocalDate.of(2026, 2, 28);

        ledgerEntryRepository.save(entry(5001L, "USDT", "1", EntryType.TRADE, "D1", d1.atTime(9, 0)));
        accountBalanceRepository.save(balance(5001L, "USDT", "1", "0"));
        reconBatchRunner.runStandalone(d1);

        ReconBatch b1 = reconBatchRepository
                .findTopByClearingBatchIdAndBusinessDateOrderByAttemptDesc(0L, d1)
                .orElseThrow();

        accountBalanceRepository.deleteAll();
        ledgerEntryRepository.save(entry(5002L, "USDT", "2", EntryType.TRADE, "D2", d2.atTime(9, 0)));
        accountBalanceRepository.save(balance(5002L, "USDT", "2", "0"));
        reconBatchRunner.runStandalone(d2);

        ReconBatch b2 = reconBatchRepository
                .findTopByClearingBatchIdAndBusinessDateOrderByAttemptDesc(0L, d2)
                .orElseThrow();

        assertThat(b2.getId()).isNotEqualTo(b1.getId());
        assertThat(b2.getBusinessDate()).isEqualTo(d2);
        assertThat(b2.getAttempt()).isGreaterThan(b1.getAttempt()); // global standalone attempt namespace
        assertThat(b1.getBusinessDate()).isEqualTo(d1);
    }

    @Test
    void runMostRecentSuccessClearing_fallsBackToStandalone_whenNoClearingBatchExists() {
        LocalDate businessDate = LocalDate.of(2026, 3, 1);
        when(clearingBatchRepository.findTopByBusinessDateAndBatchTypeAndScopeOrderByIdDesc(
                businessDate, ClearingBatchType.EOD, ""))
                .thenReturn(Optional.empty());

        ledgerEntryRepository.save(entry(6001L, "USDT", "10", EntryType.TRADE, "FB-RCN-1", businessDate.atTime(9, 0)));
        accountBalanceRepository.save(balance(6001L, "USDT", "8", "0"));

        reconBatchRunner.runMostRecentSuccessClearing(businessDate);

        ReconBatch batch = reconBatchRepository
                .findTopByClearingBatchIdAndBusinessDateOrderByAttemptDesc(0L, businessDate)
                .orElseThrow();
        assertThat(batch.getStatus()).isEqualTo(ReconBatchStatus.SUCCESS);
        assertThat(batch.getDiffCount()).isEqualTo(1L);
        assertThat(reconDiffRepository.countByReconBatchId(batch.getId())).isEqualTo(1L);
    }

    @Test
    void runForClearingBatch_skips_whenClearingBatchIsNotSuccess() {
        LocalDate businessDate = LocalDate.of(2026, 3, 2);
        ClearingBatch pendingClearing = ClearingBatch.pending(businessDate, ClearingBatchType.EOD, "EOD", 1, "");
        setField(pendingClearing, "id", 999L);
        // status already PENDING by factory

        when(clearingBatchRepository.findById(999L)).thenReturn(Optional.of(pendingClearing));

        reconBatchRunner.runForClearingBatch(999L);

        assertThat(reconBatchRepository.findAll()).isEmpty();
        assertThat(reconDiffRepository.findAll()).isEmpty();
    }

    private LedgerEntry entry(Long accountId, String asset, String amount, EntryType type, String tradeId, LocalDateTime createdAt) {
        return LedgerEntry.builder()
                .accountId(accountId)
                .asset(asset)
                .amount(new BigDecimal(amount))
                .entryType(type)
                .tradeId(tradeId)
                .idempotencyKey(tradeId + ":" + asset + ":" + type)
                .createdAt(createdAt)
                .build();
    }

    private AccountBalance balance(Long accountId, String asset, String available, String locked) {
        return AccountBalance.builder()
                .accountId(accountId)
                .asset(asset)
                .available(new BigDecimal(available))
                .locked(new BigDecimal(locked))
                .build();
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "org.redisson.spring.starter.RedissonAutoConfigurationV2"
    })
    @EntityScan(basePackageClasses = {
            ReconBatch.class,
            ReconDiff.class,
            LedgerEntry.class,
            AccountBalance.class
    })
    @EnableJpaRepositories(basePackageClasses = {
            ReconBatchRepository.class,
            ReconDiffRepository.class,
            LedgerEntryRepository.class,
            AccountBalanceRepository.class
    })
    @Import({
            ReconBatchRunner.class,
            ReconBatchService.class,
            DbReconInputsPort.class,
            ReconComparator.class,
            ReconPrecisionPolicy.class,
            ReconDiffWriter.class
    })
    static class ReconIntegrationTestApp {
    }
}
