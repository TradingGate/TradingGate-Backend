package org.tradinggate.backend.risk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.risk.domain.entity.balance.AccountBalance;
import org.tradinggate.backend.risk.domain.entity.ledger.EntryType;
import org.tradinggate.backend.risk.domain.entity.ledger.LedgerEntry;
import org.tradinggate.backend.risk.repository.balance.AccountBalanceRepository;
import org.tradinggate.backend.risk.repository.ledger.LedgerEntryRepository;
import org.tradinggate.backend.risk.service.balance.BalanceService;
import org.tradinggate.backend.risk.service.ledger.LedgerService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 대사(Reconciliation) 테스트
 * 
 * 검증 목표:
 * - 원장(Ledger) 합계 = 잔고(Balance) 일치 여부
 * - 누락/중복 탐지
 * - 정합성 보장
 */
@SpringBootTest
@ActiveProfiles("risk")
@Transactional
public class LedgerReconciliationTest {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private LedgerEntryRepository ledgerRepository;

    @Autowired
    private AccountBalanceRepository balanceRepository;

    private static final Long ACCOUNT_ID = 2000L;

    @BeforeEach
    void setUp() {
        ledgerRepository.deleteAll();
        balanceRepository.deleteAll();
    }

    @Test
    @DisplayName("대사: 원장 합계 = 잔고 일치 (BTC 단일 자산)")
    void testReconciliation_SingleAsset() {
        // Given: BTC 3회 거래
        recordTrade("T1", ACCOUNT_ID, "BTC", "0.1");
        recordTrade("T2", ACCOUNT_ID, "BTC", "0.2");
        recordTrade("T3", ACCOUNT_ID, "BTC", "-0.05");

        // When: 원장 합계 계산
        BigDecimal ledgerSum = calculateLedgerSum(ACCOUNT_ID, "BTC");
        BigDecimal balanceAmount = getBalanceAmount(ACCOUNT_ID, "BTC");

        // Then: 일치 확인
        assertThat(ledgerSum).isEqualByComparingTo("0.25"); // 0.1 + 0.2 - 0.05
        assertThat(balanceAmount).isEqualByComparingTo(ledgerSum);
    }

    @Test
    @DisplayName("대사: 다중 자산 동시 검증")
    void testReconciliation_MultipleAssets() {
        // Given
        recordTrade("T1", ACCOUNT_ID, "BTC", "1.0");
        recordTrade("T2", ACCOUNT_ID, "ETH", "10.0");
        recordTrade("T3", ACCOUNT_ID, "USDT", "50000");
        recordTrade("T4", ACCOUNT_ID, "BTC", "-0.5");
        recordTrade("T5", ACCOUNT_ID, "ETH", "5.0");

        // When & Then: 각 자산별 대사
        assertReconciled(ACCOUNT_ID, "BTC", "0.5");
        assertReconciled(ACCOUNT_ID, "ETH", "15.0");
        assertReconciled(ACCOUNT_ID, "USDT", "50000");
    }

    @Test
    @DisplayName("대사: 수수료 포함 검증")
    void testReconciliation_WithFees() {
        // Given: BTC 매수 + USDT 수수료
        String tradeId = "T-FEE-001";
        ledgerService.recordEntry(ACCOUNT_ID, "BTC", new BigDecimal("0.1"), EntryType.TRADE, tradeId,
                LedgerEntry.generateIdempotencyKey(tradeId, ACCOUNT_ID, "BTC", EntryType.TRADE));
        ledgerService.recordEntry(ACCOUNT_ID, "USDT", new BigDecimal("-5000"), EntryType.TRADE, tradeId,
                LedgerEntry.generateIdempotencyKey(tradeId, ACCOUNT_ID, "USDT", EntryType.TRADE));
        ledgerService.recordEntry(ACCOUNT_ID, "USDT", new BigDecimal("-5"), EntryType.FEE, tradeId,
                LedgerEntry.generateIdempotencyKey(tradeId, ACCOUNT_ID, "USDT", EntryType.FEE));

        // Balance 업데이트
        Map<String, BigDecimal> changes = new HashMap<>();
        changes.put("BTC", new BigDecimal("0.1"));
        changes.put("USDT", new BigDecimal("-5005")); // -5000 - 5
        balanceService.updateBalances(ACCOUNT_ID, changes);

        // When & Then
        assertReconciled(ACCOUNT_ID, "BTC", "0.1");
        assertReconciled(ACCOUNT_ID, "USDT", "-5005");
    }

    @Test
    @DisplayName("대사: 누락 탐지 - 원장 있지만 잔고 없음")
    void testReconciliation_MissingBalance() {
        // Given: 원장만 기록, 잔고 업데이트 누락
        String tradeId = "T-MISSING";
        ledgerService.recordEntry(ACCOUNT_ID, "BNB", new BigDecimal("100"), EntryType.TRADE, tradeId,
                LedgerEntry.generateIdempotencyKey(tradeId, ACCOUNT_ID, "BNB", EntryType.TRADE));

        // When
        BigDecimal ledgerSum = calculateLedgerSum(ACCOUNT_ID, "BNB");
        BigDecimal balanceAmount = getBalanceAmount(ACCOUNT_ID, "BNB");

        // Then: 불일치 탐지
        assertThat(ledgerSum).isEqualByComparingTo("100");
        assertThat(balanceAmount).isEqualByComparingTo("0"); // 잔고 없음
        assertThat(ledgerSum).isNotEqualByComparingTo(balanceAmount);
    }

    @Test
    @DisplayName("대사: 중복 탐지 - 멱등성 키로 방지")
    void testReconciliation_DuplicatePrevention() {
        // Given
        String tradeId = "T-DUP";
        String idempotencyKey = LedgerEntry.generateIdempotencyKey(tradeId, ACCOUNT_ID, "SOL", EntryType.TRADE);

        // When: 같은 키로 2회 기록 시도
        LedgerEntry first = ledgerService.recordEntry(ACCOUNT_ID, "SOL", new BigDecimal("50"),
                EntryType.TRADE, tradeId, idempotencyKey);
        LedgerEntry second = ledgerService.recordEntry(ACCOUNT_ID, "SOL", new BigDecimal("50"),
                EntryType.TRADE, tradeId, idempotencyKey);

        // Then
        assertThat(first).isNotNull();
        assertThat(second).isNull(); // 중복 방지됨

        List<LedgerEntry> entries = ledgerRepository.findByAccountIdAndAssetOrderByCreatedAtDesc(ACCOUNT_ID, "SOL");
        assertThat(entries).hasSize(1); // 1개만 기록됨
    }

    @Test
    @DisplayName("대사: 소수점 오차 허용 (USDT 반올림)")
    void testReconciliation_RoundingTolerance() {
        // Given: 미세한 소수점 오차 발생 가능
        recordTrade("T1", ACCOUNT_ID, "USDT", "1000.12345678");
        recordTrade("T2", ACCOUNT_ID, "USDT", "2000.87654321");

        // When
        BigDecimal ledgerSum = calculateLedgerSum(ACCOUNT_ID, "USDT");
        BigDecimal balanceAmount = getBalanceAmount(ACCOUNT_ID, "USDT");

        // Then: 10^-8 이내 오차 허용
        assertThat(ledgerSum.doubleValue())
                .isCloseTo(balanceAmount.doubleValue(), within(0.00000001));
    }

    @Test
    @DisplayName("대사: 계정별 전체 자산 재구성")
    void testReconciliation_FullAccountReconstruction() {
        // Given: 다양한 거래
        recordTrade("T1", ACCOUNT_ID, "BTC", "1.0");
        recordTrade("T2", ACCOUNT_ID, "BTC", "0.5");
        recordTrade("T3", ACCOUNT_ID, "ETH", "10.0");
        recordTrade("T4", ACCOUNT_ID, "USDT", "100000");
        recordTrade("T5", ACCOUNT_ID, "BTC", "-0.3");

        // When: 계정 전체 원장 조회
        List<LedgerEntry> allLedgers = ledgerRepository.findByAccountIdOrderByCreatedAtAsc(ACCOUNT_ID);
        assertThat(allLedgers).hasSize(5);

        // When: 자산별 재구성
        Map<String, BigDecimal> reconstructed = reconstructBalanceFromLedger(ACCOUNT_ID);

        // Then: 검증
        assertThat(reconstructed.get("BTC")).isEqualByComparingTo("1.2"); // 1.0 + 0.5 - 0.3
        assertThat(reconstructed.get("ETH")).isEqualByComparingTo("10.0");
        assertThat(reconstructed.get("USDT")).isEqualByComparingTo("100000");

        // DB 잔고와 일치 확인
        assertThat(getBalanceAmount(ACCOUNT_ID, "BTC")).isEqualByComparingTo(reconstructed.get("BTC"));
        assertThat(getBalanceAmount(ACCOUNT_ID, "ETH")).isEqualByComparingTo(reconstructed.get("ETH"));
        assertThat(getBalanceAmount(ACCOUNT_ID, "USDT")).isEqualByComparingTo(reconstructed.get("USDT"));
    }

    // === Helper Methods ===

    private void recordTrade(String tradeId, Long accountId, String asset, String amount) {
        String idempotencyKey = LedgerEntry.generateIdempotencyKey(tradeId, accountId, asset, EntryType.TRADE);
        ledgerService.recordEntry(accountId, asset, new BigDecimal(amount), EntryType.TRADE, tradeId, idempotencyKey);
        balanceService.updateBalance(accountId, asset, new BigDecimal(amount));
    }

    private BigDecimal calculateLedgerSum(Long accountId, String asset) {
        List<LedgerEntry> entries = ledgerRepository.findByAccountIdAndAssetOrderByCreatedAtDesc(accountId, asset);
        return entries.stream()
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getBalanceAmount(Long accountId, String asset) {
        return balanceRepository.findByAccountIdAndAsset(accountId, asset)
                .map(AccountBalance::getAvailable)
                .orElse(BigDecimal.ZERO);
    }

    private void assertReconciled(Long accountId, String asset, String expected) {
        BigDecimal ledgerSum = calculateLedgerSum(accountId, asset);
        BigDecimal balanceAmount = getBalanceAmount(accountId, asset);
        assertThat(ledgerSum).isEqualByComparingTo(expected);
        assertThat(balanceAmount).isEqualByComparingTo(ledgerSum);
    }

    private Map<String, BigDecimal> reconstructBalanceFromLedger(Long accountId) {
        List<LedgerEntry> entries = ledgerRepository.findByAccountIdOrderByCreatedAtAsc(accountId);
        Map<String, BigDecimal> result = new HashMap<>();

        for (LedgerEntry entry : entries) {
            String asset = entry.getAsset();
            result.put(asset, result.getOrDefault(asset, BigDecimal.ZERO).add(entry.getAmount()));
        }

        return result;
    }
}
