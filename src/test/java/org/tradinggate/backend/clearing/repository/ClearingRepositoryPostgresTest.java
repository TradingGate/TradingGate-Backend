package org.tradinggate.backend.clearing.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.ClearingBatch;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.ClearingResult;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchStatus;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingResultStatus;
import org.tradinggate.backend.settlementIntegrity.clearing.repository.ClearingBatchRepository;
import org.tradinggate.backend.settlementIntegrity.clearing.repository.ClearingResultRepository;
import org.tradinggate.backend.support.PostgresTcBase;
import org.tradinggate.backend.risk.domain.entity.ledger.EntryType;
import org.tradinggate.backend.risk.domain.entity.ledger.LedgerEntry;
import org.tradinggate.backend.risk.repository.ledger.LedgerEntryRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ClearingRepositoryPostgresTest.JpaRepoTestApp.class, properties = "spring.index.ignore=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class ClearingRepositoryPostgresTest extends PostgresTcBase {

    @Autowired
    private ClearingBatchRepository clearingBatchRepository;

    @Autowired
    private ClearingResultRepository clearingResultRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        clearingResultRepository.deleteAll();
        clearingBatchRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
    }

    @Test
    void tryMarkRunningWithDbWatermark_setsRunningAndWatermarkAtomically() {
        LocalDate businessDate = LocalDate.of(2026, 2, 23);
        ClearingBatch batch = clearingBatchRepository.saveAndFlush(
                ClearingBatch.pending(businessDate, ClearingBatchType.EOD, "EOD", 1, "")
        );

        ledgerEntryRepository.save(entry(1L, "USDT", "100", EntryType.TRADE, "T-1",
                LocalDateTime.of(2026, 2, 23, 10, 0)));
        ledgerEntryRepository.save(entry(1L, "USDT", "50", EntryType.TRADE, "T-2",
                LocalDateTime.of(2026, 2, 23, 20, 0)));
        ledgerEntryRepository.save(entry(1L, "USDT", "999", EntryType.TRADE, "T-3",
                LocalDateTime.of(2026, 2, 24, 0, 0))); // boundary excluded (< next day)

        int updated = clearingBatchRepository.tryMarkRunningWithDbWatermark(
                batch.getId(),
                ClearingBatchStatus.PENDING.name(),
                ClearingBatchStatus.RUNNING.name(),
                Instant.now()
        );

        assertThat(updated).isEqualTo(1);

        ClearingBatch reloaded = clearingBatchRepository.findById(batch.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ClearingBatchStatus.RUNNING);
        assertThat(reloaded.getSnapshotKey()).startsWith("WM-");
        assertThat(reloaded.getCutoffOffsets()).containsKey("max_ledger_id");

        Long maxIncludedId = ledgerEntryRepository.findMaxIdBefore(businessDate.plusDays(1).atStartOfDay());
        assertThat(reloaded.getCutoffOffsets().get("max_ledger_id")).isEqualTo(maxIncludedId);
    }

    @Test
    void tryMarkRunningWithDbWatermark_returnsZero_whenBatchAlreadyRunning() {
        LocalDate businessDate = LocalDate.of(2026, 2, 23);
        ClearingBatch batch = clearingBatchRepository.saveAndFlush(
                ClearingBatch.pending(businessDate, ClearingBatchType.EOD, "EOD", 1, "")
        );

        int first = clearingBatchRepository.tryMarkRunningWithDbWatermark(
                batch.getId(),
                ClearingBatchStatus.PENDING.name(),
                ClearingBatchStatus.RUNNING.name(),
                Instant.now()
        );
        int second = clearingBatchRepository.tryMarkRunningWithDbWatermark(
                batch.getId(),
                ClearingBatchStatus.PENDING.name(),
                ClearingBatchStatus.RUNNING.name(),
                Instant.now()
        );

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(0);
    }

    @Test
    void tryMarkRunningWithDbWatermark_setsZeroWatermark_whenNoLedgerExistsBeforeCutoff() {
        LocalDate businessDate = LocalDate.of(2026, 2, 23);
        ClearingBatch batch = clearingBatchRepository.saveAndFlush(
                ClearingBatch.pending(businessDate, ClearingBatchType.EOD, "EOD", 1, "")
        );

        // cutoff 이전 ledger 없음 (boundary equal and after only)
        ledgerEntryRepository.save(entry(1L, "USDT", "10", EntryType.TRADE, "T-B0",
                businessDate.plusDays(1).atStartOfDay()));
        ledgerEntryRepository.save(entry(1L, "USDT", "20", EntryType.TRADE, "T-B1",
                businessDate.plusDays(1).atTime(0, 1)));

        int updated = clearingBatchRepository.tryMarkRunningWithDbWatermark(
                batch.getId(),
                ClearingBatchStatus.PENDING.name(),
                ClearingBatchStatus.RUNNING.name(),
                Instant.now()
        );

        assertThat(updated).isEqualTo(1);
        ClearingBatch reloaded = clearingBatchRepository.findById(batch.getId()).orElseThrow();
        assertThat(reloaded.getCutoffOffsets()).containsEntry("max_ledger_id", 0L);
    }

    @Test
    void findPreviousClosingBalances_returnsLatestPriorEodFinalFirst() throws Exception {
        LocalDate businessDate = LocalDate.of(2026, 2, 23);
        ClearingBatch eodBatch1 = clearingBatchRepository.saveAndFlush(
                ClearingBatch.pending(LocalDate.of(2026, 2, 21), ClearingBatchType.EOD, "EOD", 1, "")
        );
        markBatchSuccess(eodBatch1.getId());
        ClearingBatch eodBatch2 = clearingBatchRepository.saveAndFlush(
                ClearingBatch.pending(LocalDate.of(2026, 2, 22), ClearingBatchType.EOD, "EOD", 1, "")
        );
        markBatchSuccess(eodBatch2.getId());
        ClearingBatch intradayBatch = clearingBatchRepository.saveAndFlush(
                ClearingBatch.pending(LocalDate.of(2026, 2, 22), ClearingBatchType.INTRADAY, "INTRADAY-1", 1, "")
        );
        markBatchSuccess(intradayBatch.getId());

        insertClearingResult(eodBatch1.getId(), LocalDate.of(2026, 2, 21), 1001L, "USDT", "100", "FINAL");
        insertClearingResult(eodBatch2.getId(), LocalDate.of(2026, 2, 22), 1001L, "usdt", "150", "FINAL");
        insertClearingResult(intradayBatch.getId(), LocalDate.of(2026, 2, 22), 1001L, "USDT", "999", "PRELIMINARY");

        List<BigDecimal> balances = clearingResultRepository.findPreviousClosingBalances(
                businessDate, 1001L, "USDT", ClearingResultStatus.FINAL, ClearingBatchType.EOD
        );

        assertThat(balances).isNotEmpty();
        assertThat(balances.get(0)).isEqualByComparingTo("150");
        assertThat(balances).anySatisfy(v -> assertThat(v).isEqualByComparingTo("100"));
        assertThat(balances).noneSatisfy(v -> assertThat(v).isEqualByComparingTo("999"));
    }

    @Test
    void findPreviousClosingBalances_filtersByAccountAssetBatchTypeAndStatus_andOrdersByDateThenId() {
        LocalDate businessDate = LocalDate.of(2026, 2, 23);

        ClearingBatch eodBatchOld = clearingBatchRepository.saveAndFlush(
                ClearingBatch.pending(LocalDate.of(2026, 2, 21), ClearingBatchType.EOD, "EOD", 1, "")
        );
        ClearingBatch eodBatchDateA = clearingBatchRepository.saveAndFlush(
                ClearingBatch.pending(LocalDate.of(2026, 2, 22), ClearingBatchType.EOD, "EOD", 1, "")
        );
        ClearingBatch eodBatchDateB = clearingBatchRepository.saveAndFlush(
                ClearingBatch.pending(LocalDate.of(2026, 2, 22), ClearingBatchType.EOD, "EOD", 2, "")
        );
        ClearingBatch eodBatchPrelim = clearingBatchRepository.saveAndFlush(
                ClearingBatch.pending(LocalDate.of(2026, 2, 22), ClearingBatchType.EOD, "EOD", 3, "")
        );
        ClearingBatch intraday = clearingBatchRepository.saveAndFlush(
                ClearingBatch.pending(LocalDate.of(2026, 2, 22), ClearingBatchType.INTRADAY, "I-1", 1, "")
        );
        for (ClearingBatch b : List.of(eodBatchOld, eodBatchDateA, eodBatchDateB, eodBatchPrelim, intraday)) {
            markBatchSuccess(b.getId());
        }

        insertClearingResult(eodBatchOld.getId(), LocalDate.of(2026, 2, 21), 1001L, "USDT", "90", "FINAL");
        insertClearingResult(eodBatchDateA.getId(), LocalDate.of(2026, 2, 22), 1001L, "USDT", "100", "FINAL");
        insertClearingResult(eodBatchDateB.getId(), LocalDate.of(2026, 2, 22), 1001L, "USDT", "110", "FINAL"); // same date, later id
        insertClearingResult(intraday.getId(), LocalDate.of(2026, 2, 22), 1001L, "USDT", "999", "FINAL"); // batch type excluded
        insertClearingResult(eodBatchDateB.getId(), LocalDate.of(2026, 2, 22), 1001L, "BTC", "777", "FINAL"); // asset excluded
        insertClearingResult(eodBatchDateB.getId(), LocalDate.of(2026, 2, 22), 9999L, "USDT", "666", "FINAL"); // account excluded
        insertClearingResult(eodBatchPrelim.getId(), LocalDate.of(2026, 2, 22), 1001L, "USDT", "555", "PRELIMINARY"); // status excluded

        List<BigDecimal> balances = clearingResultRepository.findPreviousClosingBalances(
                businessDate, 1001L, "usdt", ClearingResultStatus.FINAL, ClearingBatchType.EOD
        );

        assertThat(balances).hasSize(3);
        assertThat(balances.get(0)).isEqualByComparingTo("110");
        assertThat(balances.get(1)).isEqualByComparingTo("100");
        assertThat(balances.get(2)).isEqualByComparingTo("90");
    }

    @Test
    void findPreviousClosingBalances_returnsEmpty_whenNoMatchingRows() {
        ClearingBatch batch = clearingBatchRepository.saveAndFlush(
                ClearingBatch.pending(LocalDate.of(2026, 2, 22), ClearingBatchType.EOD, "EOD", 1, "")
        );
        markBatchSuccess(batch.getId());
        insertClearingResult(batch.getId(), LocalDate.of(2026, 2, 22), 1001L, "USDT", "100", "FINAL");

        List<BigDecimal> balances = clearingResultRepository.findPreviousClosingBalances(
                LocalDate.of(2026, 2, 22), 1001L, "USDT", ClearingResultStatus.FINAL, ClearingBatchType.EOD
        );

        assertThat(balances).isEmpty(); // same business date is not previous
    }

    @Test
    void aggregateDailyLedger_computesPeriodNetFeeTradeValueAndCount_withWatermarkBoundary() {
        LocalDate businessDate = LocalDate.of(2026, 2, 23);
        LocalDateTime start = businessDate.atStartOfDay();
        LocalDateTime end = businessDate.plusDays(1).atStartOfDay();

        LedgerEntry t1 = ledgerEntryRepository.save(entry(1001L, "USDT", "-100", EntryType.TRADE, "TR-1",
                businessDate.atTime(10, 0)));
        ledgerEntryRepository.save(entry(1001L, "USDT", "-2", EntryType.FEE, "TR-1",
                businessDate.atTime(10, 0, 1)));
        LedgerEntry t2 = ledgerEntryRepository.save(entry(1001L, "USDT", "40", EntryType.TRADE, "TR-2",
                businessDate.atTime(11, 0)));
        ledgerEntryRepository.save(entry(1001L, "USDT", "999", EntryType.TRADE, "TR-3",
                businessDate.plusDays(1).atStartOfDay())); // date boundary excluded

        List<Object[]> rows = ledgerEntryRepository.aggregateDailyLedger(start, end, t2.getId());

        assertThat(rows).hasSize(1);
        Object[] row = rows.get(0);
        assertThat(row[0]).isEqualTo(1001L);
        assertThat(row[1]).isEqualTo("USDT");
        assertThat((BigDecimal) row[2]).isEqualByComparingTo("-62"); // -100 -2 +40
        assertThat((BigDecimal) row[3]).isEqualByComparingTo("2");
        assertThat((BigDecimal) row[4]).isEqualByComparingTo("140"); // abs(-100)+abs(40)
        assertThat(((Number) row[5]).longValue()).isEqualTo(2L);
        assertThat(t1.getId()).isLessThanOrEqualTo(t2.getId());
    }

    @Test
    void sumAllHoldingsUpTo_and_sumByAccountAssetUpToId_respectWatermark() {
        LedgerEntry first = ledgerEntryRepository.save(entry(1001L, "USDT", "100", EntryType.TRADE, "T-H1",
                LocalDateTime.of(2026, 2, 23, 9, 0)));
        ledgerEntryRepository.save(entry(1001L, "USDT", "-30", EntryType.FEE, "T-H2",
                LocalDateTime.of(2026, 2, 23, 10, 0)));
        ledgerEntryRepository.save(entry(1002L, "BTC", "1.5", EntryType.TRADE, "T-H3",
                LocalDateTime.of(2026, 2, 23, 11, 0)));

        List<Object[]> holdings = ledgerEntryRepository.sumAllHoldingsUpTo(first.getId());
        assertThat(holdings).hasSize(1);
        assertThat(holdings.get(0)[0]).isEqualTo(1001L);
        assertThat(holdings.get(0)[1]).isEqualTo("USDT");
        assertThat((BigDecimal) holdings.get(0)[2]).isEqualByComparingTo("100");

        BigDecimal usdtAtSecond = ledgerEntryRepository.sumByAccountIdAndAssetUpToId(1001L, "usdt", first.getId() + 1);
        assertThat(usdtAtSecond).isEqualByComparingTo("70");
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

    private void markBatchSuccess(Long batchId) {
        clearingBatchRepository.markSuccess(batchId, ClearingBatchStatus.SUCCESS, Instant.now());
    }

    private void insertClearingResult(Long batchId, LocalDate businessDate, Long accountId, String asset, String closingBalance, String status) {
        jdbcTemplate.update("""
            insert into clearing_result (
                batch_id, business_date, account_id, asset,
                opening_balance, closing_balance, net_change,
                fee_total, trade_count, trade_value,
                status, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            """,
                batchId,
                businessDate,
                accountId,
                asset,
                BigDecimal.ZERO,
                new BigDecimal(closingBalance),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0L,
                BigDecimal.ZERO,
                status
        );
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "org.redisson.spring.starter.RedissonAutoConfigurationV2"
    })
    @EntityScan(basePackageClasses = {
            ClearingBatch.class,
            ClearingResult.class,
            LedgerEntry.class
    })
    @EnableJpaRepositories(basePackageClasses = {
            ClearingBatchRepository.class,
            ClearingResultRepository.class,
            LedgerEntryRepository.class
    })
    static class JpaRepoTestApp {
    }
}
