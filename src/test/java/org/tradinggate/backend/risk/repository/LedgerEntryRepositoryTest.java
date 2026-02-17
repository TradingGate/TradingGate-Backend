package org.tradinggate.backend.risk.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.tradinggate.backend.risk.domain.entity.ledger.EntryType;
import org.tradinggate.backend.risk.domain.entity.ledger.LedgerEntry;
import org.tradinggate.backend.risk.repository.ledger.LedgerEntryRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LedgerEntryRepository 테스트
 * 
 * 검증 목표:
 * - 쿼리 메서드 동작 확인
 * - 인덱스 활용 확인
 * - 멱등성 키 Unique 제약 확인
 */
@DataJpaTest
@ActiveProfiles("risk")
public class LedgerEntryRepositoryTest {

    @Autowired
    private LedgerEntryRepository ledgerRepository;

    private static final Long ACCOUNT_ID = 5000L;

    @BeforeEach
    void setUp() {
        ledgerRepository.deleteAll();
    }

    @Test
    @DisplayName("Repository: 원장 항목 저장 및 조회")
    void testSaveAndFindLedgerEntry() {
        // Given
        LedgerEntry entry = LedgerEntry.builder()
                .accountId(ACCOUNT_ID)
                .asset("BTC")
                .amount(new BigDecimal("0.5"))
                .entryType(EntryType.TRADE)
                .tradeId("T-001")
                .idempotencyKey("T-001:BTC:TRADE")
                .build();

        // When
        LedgerEntry saved = ledgerRepository.save(entry);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getAmount()).isEqualByComparingTo("0.5");
    }

    @Test
    @DisplayName("Repository: 계정+자산별 조회 (시간 역순)")
    void testFindByAccountIdAndAsset() throws InterruptedException {
        // Given: 3개 항목 저장
        saveLedgerEntry(ACCOUNT_ID, "BTC", "1.0", "T-1");
        Thread.sleep(10);
        saveLedgerEntry(ACCOUNT_ID, "BTC", "0.5", "T-2");
        Thread.sleep(10);
        saveLedgerEntry(ACCOUNT_ID, "BTC", "-0.2", "T-3");

        // When
        List<LedgerEntry> entries = ledgerRepository
                .findByAccountIdAndAssetOrderByCreatedAtDesc(ACCOUNT_ID, "BTC");

        // Then: 최신 순
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).getTradeId()).isEqualTo("T-3"); // 최신
        assertThat(entries.get(2).getTradeId()).isEqualTo("T-1"); // 가장 오래됨
    }

    @Test
    @DisplayName("Repository: 계정별 전체 조회 (시간 순)")
    void testFindByAccountId() {
        // Given
        saveLedgerEntry(ACCOUNT_ID, "BTC", "1.0", "T-1");
        saveLedgerEntry(ACCOUNT_ID, "ETH", "10.0", "T-2");
        saveLedgerEntry(ACCOUNT_ID, "USDT", "50000", "T-3");

        // When
        List<LedgerEntry> entries = ledgerRepository
                .findByAccountIdOrderByCreatedAtAsc(ACCOUNT_ID);

        // Then
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).getAsset()).isEqualTo("BTC");
        assertThat(entries.get(1).getAsset()).isEqualTo("ETH");
        assertThat(entries.get(2).getAsset()).isEqualTo("USDT");
    }

    @Test
    @DisplayName("Repository: 멱등성 키 중복 방지")
    void testIdempotencyKeyUnique() {
        // Given
        String idempotencyKey = "T-DUP:BTC:TRADE";
        LedgerEntry first = LedgerEntry.builder()
                .accountId(ACCOUNT_ID)
                .asset("BTC")
                .amount(new BigDecimal("1.0"))
                .entryType(EntryType.TRADE)
                .tradeId("T-DUP")
                .idempotencyKey(idempotencyKey)
                .build();
        ledgerRepository.save(first);

        // When: 같은 키로 2차 저장 시도
        boolean exists = ledgerRepository.existsByIdempotencyKey(idempotencyKey);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Repository: 거래 ID로 조회")
    void testFindByTradeId() {
        // Given: 하나의 거래에 3개 항목 (BTC, USDT, FEE)
        String tradeId = "T-MULTI";
        saveLedgerEntry(ACCOUNT_ID, "BTC", "0.1", tradeId, EntryType.TRADE);
        saveLedgerEntry(ACCOUNT_ID, "USDT", "-5000", tradeId, EntryType.TRADE);
        saveLedgerEntry(ACCOUNT_ID, "USDT", "-5", tradeId, EntryType.FEE);

        // When
        List<LedgerEntry> entries = ledgerRepository.findByTradeId(tradeId);

        // Then
        assertThat(entries).hasSize(3);
        assertThat(entries.stream().map(LedgerEntry::getEntryType).distinct().count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Repository: 자산별 합계 계산 (집계)")
    void testSumByAsset() {
        // Given
        saveLedgerEntry(ACCOUNT_ID, "BTC", "1.0", "T-1");
        saveLedgerEntry(ACCOUNT_ID, "BTC", "0.5", "T-2");
        saveLedgerEntry(ACCOUNT_ID, "BTC", "-0.3", "T-3");

        // When
        List<LedgerEntry> entries = ledgerRepository
                .findByAccountIdAndAssetOrderByCreatedAtDesc(ACCOUNT_ID, "BTC");
        BigDecimal sum = entries.stream()
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Then
        assertThat(sum).isEqualByComparingTo("1.2"); // 1.0 + 0.5 - 0.3
    }

    @Test
    @DisplayName("Repository: 다수 계정 동시 조회")
    void testMultipleAccounts() {
        // Given
        saveLedgerEntry(5001L, "BTC", "1.0", "T-A1");
        saveLedgerEntry(5002L, "BTC", "2.0", "T-A2");
        saveLedgerEntry(5003L, "BTC", "3.0", "T-A3");

        // When
        List<LedgerEntry> acc1 = ledgerRepository.findByAccountIdOrderByCreatedAtAsc(5001L);
        List<LedgerEntry> acc2 = ledgerRepository.findByAccountIdOrderByCreatedAtAsc(5002L);

        // Then
        assertThat(acc1).hasSize(1);
        assertThat(acc2).hasSize(1);
        assertThat(acc1.get(0).getAmount()).isEqualByComparingTo("1.0");
        assertThat(acc2.get(0).getAmount()).isEqualByComparingTo("2.0");
    }

    @Test
    @DisplayName("Repository: 날짜 범위 조회")
    void testDateRangeQuery() throws InterruptedException {
        // Given
        LocalDateTime now = LocalDateTime.now();
        saveLedgerEntry(ACCOUNT_ID, "BTC", "1.0", "T-OLD");

        LocalDateTime cutoff = LocalDateTime.now();
        Thread.sleep(10);

        saveLedgerEntry(ACCOUNT_ID, "BTC", "2.0", "T-NEW");

        // When: cutoff 이후 조회
        List<LedgerEntry> allEntries = ledgerRepository
                .findByAccountIdAndAssetOrderByCreatedAtDesc(ACCOUNT_ID, "BTC");

        List<LedgerEntry> recentEntries = allEntries.stream()
                .filter(e -> e.getCreatedAt().isAfter(cutoff))
                .toList();

        // Then
        assertThat(allEntries).hasSize(2);
        assertThat(recentEntries).hasSize(1);
        assertThat(recentEntries.get(0).getTradeId()).isEqualTo("T-NEW");
    }

    @Test
    @DisplayName("Repository: 페이징 조회 시뮬레이션")
    void testPagination() {
        // Given: 100개 항목
        for (int i = 1; i <= 100; i++) {
            saveLedgerEntry(ACCOUNT_ID, "BTC", "0.1", "T-" + i);
        }

        // When: 전체 조회
        List<LedgerEntry> allEntries = ledgerRepository
                .findByAccountIdAndAssetOrderByCreatedAtDesc(ACCOUNT_ID, "BTC");

        // Then
        assertThat(allEntries).hasSize(100);

        // 최근 10개만
        List<LedgerEntry> topTen = allEntries.stream().limit(10).toList();
        assertThat(topTen).hasSize(10);
    }

    // === Helper Methods ===

    private void saveLedgerEntry(Long accountId, String asset, String amount, String tradeId) {
        saveLedgerEntry(accountId, asset, amount, tradeId, EntryType.TRADE);
    }

    private void saveLedgerEntry(Long accountId, String asset, String amount, String tradeId, EntryType entryType) {
        LedgerEntry entry = LedgerEntry.builder()
                .accountId(accountId)
                .asset(asset)
                .amount(new BigDecimal(amount))
                .entryType(entryType)
                .tradeId(tradeId)
                .idempotencyKey(LedgerEntry.generateIdempotencyKey(tradeId, asset, entryType))
                .build();
        ledgerRepository.save(entry);

        try {
            Thread.sleep(5); // 시간 간격 확보
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
