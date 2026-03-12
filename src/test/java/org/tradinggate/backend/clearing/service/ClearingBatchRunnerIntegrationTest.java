package org.tradinggate.backend.clearing.service;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.ClearingBatch;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.ClearingResult;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchStatus;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingFailureCode;
import org.tradinggate.backend.settlementIntegrity.clearing.policy.ClearingBatchTriggerPolicy;
import org.tradinggate.backend.settlementIntegrity.clearing.policy.ClearingTriggerDecision;
import org.tradinggate.backend.settlementIntegrity.clearing.repository.ClearingBatchRepository;
import org.tradinggate.backend.settlementIntegrity.clearing.repository.ClearingResultRepository;
import org.tradinggate.backend.settlementIntegrity.clearing.service.*;
import org.tradinggate.backend.settlementIntegrity.clearing.service.support.ClearingScopeSpecParser;
import org.tradinggate.backend.risk.domain.entity.ledger.EntryType;
import org.tradinggate.backend.risk.domain.entity.ledger.LedgerEntry;
import org.tradinggate.backend.risk.repository.ledger.LedgerEntryRepository;
import org.tradinggate.backend.support.PostgresTcBase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        classes = ClearingBatchRunnerIntegrationTest.ClearingIntegrationTestApp.class,
        properties = "spring.index.ignore=true"
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({"test", "clearing"})
class ClearingBatchRunnerIntegrationTest extends PostgresTcBase {

    @Autowired
    private ClearingBatchRunner clearingBatchRunner;

    @Autowired
    private ClearingBatchRepository clearingBatchRepository;

    @Autowired
    private ClearingResultRepository clearingResultRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ClearingOutboxService clearingOutboxService;

    private static final ClearingBatchTriggerPolicy PROCEED_POLICY = status -> ClearingTriggerDecision.proceed();

    @BeforeEach
    void setUp() {
        clearingResultRepository.deleteAll();
        clearingBatchRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
    }

    @Test
    void run_eod_writesLedgerBasedResults_andMarksBatchSuccess() {
        LocalDate businessDate = LocalDate.of(2026, 2, 23);

        // previous EOD final -> opening source for account 1001 USDT
        ClearingBatch previousEod = clearingBatchRepository.saveAndFlush(
                ClearingBatch.pending(businessDate.minusDays(1), ClearingBatchType.EOD, "EOD", 1, "")
        );
        markBatchSuccess(previousEod.getId());
        insertClearingResult(previousEod.getId(), businessDate.minusDays(1), 1001L, "USDT", "100", "FINAL");
        insertClearingResult(previousEod.getId(), businessDate.minusDays(1), 2001L, "USDT", "50", "FINAL");

        // cumulative ledger so closing snapshots match opening + periodNetChange
        ledgerEntryRepository.save(entry(1001L, "USDT", "100", EntryType.TRADE, "PREV-1", businessDate.minusDays(1).atTime(12, 0)));
        ledgerEntryRepository.save(entry(2001L, "USDT", "50", EntryType.TRADE, "PREV-2", businessDate.minusDays(1).atTime(12, 1)));

        // target day movements for 1001
        ledgerEntryRepository.save(entry(1001L, "USDT", "-100", EntryType.TRADE, "T-1", businessDate.atTime(10, 0)));
        ledgerEntryRepository.save(entry(1001L, "USDT", "-2", EntryType.FEE, "T-1", businessDate.atTime(10, 0, 1)));
        ledgerEntryRepository.save(entry(1001L, "USDT", "40", EntryType.TRADE, "T-2", businessDate.atTime(11, 0)));
        ledgerEntryRepository.save(entry(1001L, "USDT", "999", EntryType.TRADE, "T-FUTURE", businessDate.plusDays(1).atStartOfDay())); // excluded by watermark cutoff

        clearingBatchRunner.run(businessDate, ClearingBatchType.EOD, "", PROCEED_POLICY);

        ClearingBatch created = clearingBatchRepository
                .findTopByBusinessDateAndBatchTypeAndScopeOrderByIdDesc(businessDate, ClearingBatchType.EOD, "")
                .orElseThrow();

        assertThat(created.getStatus()).isEqualTo(ClearingBatchStatus.SUCCESS);
        assertThat(created.getSnapshotKey()).startsWith("WM-");
        assertThat(created.getCutoffOffsets()).containsKey("max_ledger_id");

        List<ClearingResult> results = clearingResultRepository.findByBatchId(created.getId(), PageRequest.of(0, 20)).getContent();
        assertThat(results).hasSize(2); // 1001 active + 2001 carry-over

        ClearingResult r1001 = results.stream().filter(r -> r.getAccountId().equals(1001L) && "USDT".equalsIgnoreCase(r.getAsset())).findFirst().orElseThrow();
        assertThat(r1001.getOpeningBalance()).isEqualByComparingTo("100");
        assertThat(r1001.getNetChange()).isEqualByComparingTo("-62");
        assertThat(r1001.getClosingBalance()).isEqualByComparingTo("38");
        assertThat(r1001.getFeeTotal()).isEqualByComparingTo("2");
        assertThat(r1001.getTradeCount()).isEqualTo(2L);
        assertThat(r1001.getTradeValue()).isEqualByComparingTo("140");

        ClearingResult r2001 = results.stream().filter(r -> r.getAccountId().equals(2001L) && "USDT".equalsIgnoreCase(r.getAsset())).findFirst().orElseThrow();
        assertThat(r2001.getOpeningBalance()).isEqualByComparingTo("50");
        assertThat(r2001.getNetChange()).isEqualByComparingTo("0");
        assertThat(r2001.getClosingBalance()).isEqualByComparingTo("50");
        assertThat(r2001.getTradeCount()).isZero();
        assertThat(r2001.getTradeValue()).isEqualByComparingTo("0");

        verify(clearingOutboxService).enqueueSettlementEvents(created.getId());
    }

    @Test
    void run_marksBatchFailed_whenCalculatorSanityCheckFails() {
        LocalDate businessDate = LocalDate.of(2026, 2, 24);

        // opening from previous EOD = 100, but closing from ledger cumulative = 30 and no day movement -> self-check failure
        ClearingBatch previousEod = clearingBatchRepository.saveAndFlush(
                ClearingBatch.pending(businessDate.minusDays(1), ClearingBatchType.EOD, "EOD", 1, "")
        );
        markBatchSuccess(previousEod.getId());
        insertClearingResult(previousEod.getId(), businessDate.minusDays(1), 3001L, "USDT", "100", "FINAL");

        ledgerEntryRepository.save(entry(3001L, "USDT", "30", EntryType.TRADE, "BROKEN-PREV", businessDate.minusDays(2).atTime(9, 0)));

        assertThatThrownBy(() -> clearingBatchRunner.run(businessDate, ClearingBatchType.EOD, "", PROCEED_POLICY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("opening+net!=closing");

        ClearingBatch created = clearingBatchRepository
                .findTopByBusinessDateAndBatchTypeAndScopeOrderByIdDesc(businessDate, ClearingBatchType.EOD, "")
                .orElseThrow();
        assertThat(created.getStatus()).isEqualTo(ClearingBatchStatus.FAILED);
        assertThat(created.getFailureCode()).isEqualTo(ClearingFailureCode.UNEXPECTED_ERROR);
        assertThat(clearingResultRepository.countByBatch_Id(created.getId())).isZero();
    }

    @Test
    void run_skipsWhenPolicyReturnsSkip_andLeavesBatchPending() {
        LocalDate businessDate = LocalDate.of(2026, 2, 25);
        ClearingBatchTriggerPolicy skipPolicy = status -> ClearingTriggerDecision.skip("test-skip");

        clearingBatchRunner.run(businessDate, ClearingBatchType.EOD, "", skipPolicy);

        ClearingBatch created = clearingBatchRepository
                .findTopByBusinessDateAndBatchTypeAndScopeOrderByIdDesc(businessDate, ClearingBatchType.EOD, "")
                .orElseThrow();

        assertThat(created.getStatus()).isEqualTo(ClearingBatchStatus.PENDING);
        assertThat(created.getSnapshotKey()).isNull();
        assertThat(created.getCutoffOffsets()).isEmpty();
        assertThat(clearingResultRepository.countByBatch_Id(created.getId())).isZero();
        verify(clearingOutboxService, never()).enqueueSettlementEvents(created.getId());
    }

    @Test
    void run_usesFallbackOpening_whenPreviousEodFinalDoesNotExist() {
        LocalDate businessDate = LocalDate.of(2026, 2, 26);

        // No previous clearing_result FINAL exists for 4001/USDT
        ledgerEntryRepository.save(entry(4001L, "USDT", "-10", EntryType.TRADE, "FB-1", businessDate.atTime(9, 0)));
        ledgerEntryRepository.save(entry(4001L, "USDT", "-1", EntryType.FEE, "FB-1", businessDate.atTime(9, 0, 1)));
        ledgerEntryRepository.save(entry(4001L, "USDT", "4", EntryType.TRADE, "FB-2", businessDate.atTime(10, 0)));
        // cumulative closing at watermark = -7, periodNetChange = -7, fallback opening should become 0

        clearingBatchRunner.run(businessDate, ClearingBatchType.EOD, "", PROCEED_POLICY);

        ClearingBatch created = clearingBatchRepository
                .findTopByBusinessDateAndBatchTypeAndScopeOrderByIdDesc(businessDate, ClearingBatchType.EOD, "")
                .orElseThrow();

        ClearingResult row = clearingResultRepository.findByBatchId(created.getId(), PageRequest.of(0, 10))
                .getContent().stream()
                .filter(r -> r.getAccountId().equals(4001L) && "USDT".equalsIgnoreCase(r.getAsset()))
                .findFirst()
                .orElseThrow();

        assertThat(row.getOpeningBalance()).isEqualByComparingTo("0");
        assertThat(row.getNetChange()).isEqualByComparingTo("-7");
        assertThat(row.getClosingBalance()).isEqualByComparingTo("-7");
        assertThat(row.getFeeTotal()).isEqualByComparingTo("1");
        assertThat(row.getTradeCount()).isEqualTo(2L);
        assertThat(row.getTradeValue()).isEqualByComparingTo("14");
    }

    @Test
    void run_marksBatchFailed_whenScopeFormatIsInvalid() {
        LocalDate businessDate = LocalDate.of(2026, 2, 27);

        assertThatThrownBy(() -> clearingBatchRunner.run(businessDate, ClearingBatchType.EOD, "bad-scope", PROCEED_POLICY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown scope format");

        ClearingBatch created = clearingBatchRepository
                .findTopByBusinessDateAndBatchTypeAndScopeOrderByIdDesc(businessDate, ClearingBatchType.EOD, "bad-scope")
                .orElseThrow();

        assertThat(created.getStatus()).isEqualTo(ClearingBatchStatus.FAILED);
        assertThat(created.getFailureCode()).isEqualTo(ClearingFailureCode.UNEXPECTED_ERROR);
        assertThat(clearingResultRepository.countByBatch_Id(created.getId())).isZero();
        verify(clearingOutboxService, never()).enqueueSettlementEvents(created.getId());
    }

    private void markBatchSuccess(Long batchId) {
        jdbcTemplate.update(
                "update clearing_batch set status = ?, finished_at = now(), updated_at = now() where id = ?",
                ClearingBatchStatus.SUCCESS.name(),
                batchId
        );
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
    @Import({
            ClearingBatchRunner.class,
            ClearingBatchService.class,
            ClearingResultWriter.class,
            DefaultClearingCalculator.class,
            DbClearingInputsPort.class,
            ClearingScopeSpecParser.class
    })
    static class ClearingIntegrationTestApp {
    }
}
