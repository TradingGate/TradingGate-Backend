package org.tradinggate.backend.risk.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradinggate.backend.risk.domain.entity.ledger.EntryType;
import org.tradinggate.backend.risk.domain.entity.ledger.LedgerEntry;
import org.tradinggate.backend.risk.repository.ledger.LedgerEntryRepository;
import org.tradinggate.backend.risk.service.ledger.LedgerService;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

  @Mock
  private LedgerEntryRepository ledgerRepository;

  @InjectMocks
  private LedgerService ledgerService;

  @Test
  @DisplayName("원장 기록 - 새 항목")
  void testRecordEntry_New() {
    // Given: 멱등성 키가 존재하지 않음
    when(ledgerRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
    when(ledgerRepository.save(any(LedgerEntry.class)))
        .thenAnswer(inv -> {
          LedgerEntry entry = inv.getArgument(0);
          // ID 설정 (실제 DB에서는 자동 생성)
          return LedgerEntry.builder()
              .id(1L)
              .accountId(entry.getAccountId())
              .asset(entry.getAsset())
              .amount(entry.getAmount())
              .entryType(entry.getEntryType())
              .idempotencyKey(entry.getIdempotencyKey())
              .build();
        });

    // When
    LedgerEntry result = ledgerService.recordEntry(
        1001L, "BTC", new BigDecimal("0.1"),
        EntryType.TRADE, "TRD-001", "KEY-1");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getAmount()).isEqualByComparingTo("0.1");
    assertThat(result.getAccountId()).isEqualTo(1001L);
    assertThat(result.getAsset()).isEqualTo("BTC");
    assertThat(result.getEntryType()).isEqualTo(EntryType.TRADE);
    verify(ledgerRepository, times(1)).save(any());
  }

  @Test
  @DisplayName("원장 기록 - 중복 무시")
  void testRecordEntry_Duplicate() {
    // Given: 멱등성 키가 이미 존재
    when(ledgerRepository.existsByIdempotencyKey("KEY-1")).thenReturn(true);

    // When
    LedgerEntry result = ledgerService.recordEntry(
        1001L, "BTC", new BigDecimal("0.1"),
        EntryType.TRADE, "TRD-001", "KEY-1");

    // Then: null 반환 (중복이므로 저장 안 됨)
    assertThat(result).isNull();
    verify(ledgerRepository, never()).save(any());
  }

  @Test
  @DisplayName("거래 원장 기록 - BUY")
  void testRecordTrade_Buy() {
    // Given
    when(ledgerRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
    when(ledgerRepository.save(any(LedgerEntry.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    // When: BTC 매수 (0.1 BTC @ 50000 USDT, fee 5 USDT)
    ledgerService.recordTrade(
        "TRD-001",
        1001L,
        "BTC",
        new BigDecimal("0.1"), // 받는 자산 (BTC +)
        "USDT",
        new BigDecimal("-5000"), // 지불 자산 (USDT -)
        new BigDecimal("5"), // 수수료
        "USDT" // 수수료 자산
    );

    // Then: 3번 저장 (BTC +, USDT -, FEE -)
    verify(ledgerRepository, times(3)).save(any());
  }

  @Test
  @DisplayName("거래 원장 기록 - SELL")
  void testRecordTrade_Sell() {
    // Given
    when(ledgerRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
    when(ledgerRepository.save(any(LedgerEntry.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    // When: BTC 매도 (0.5 BTC @ 50000 USDT, fee 25 USDT)
    ledgerService.recordTrade(
        "TRD-002",
        1001L,
        "BTC",
        new BigDecimal("-0.5"), // 지불 자산 (BTC -)
        "USDT",
        new BigDecimal("25000"), // 받는 자산 (USDT +)
        new BigDecimal("25"), // 수수료
        "USDT" // 수수료 자산
    );

    // Then: 3번 저장
    verify(ledgerRepository, times(3)).save(any());
  }

  @Test
  @DisplayName("거래 원장 기록 - 중복 거래 무시")
  void testRecordTrade_Duplicate() {
    // Given: 동일한 tradeId가 이미 존재 (첫 번째 호출에서 true 반환)
    when(ledgerRepository.existsByIdempotencyKey(anyString())).thenReturn(true);

    // When
    ledgerService.recordTrade(
        "TRD-DUP",
        1001L,
        "BTC",
        new BigDecimal("0.1"),
        "USDT",
        new BigDecimal("-5000"),
        new BigDecimal("5"),
        "USDT");

    // Then: 저장 안 됨
    verify(ledgerRepository, never()).save(any());
  }

  @Test
  @DisplayName("같은 tradeId라도 계정이 다르면 각각 원장 기록 가능")
  void testRecordTrade_AllowsSameTradeForDifferentAccounts() {
    when(ledgerRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
    when(ledgerRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));

    boolean makerRecorded = ledgerService.recordTrade(
        "TRD-SHARED",
        1001L,
        "BTC",
        new BigDecimal("1"),
        "USDT",
        new BigDecimal("-50000"),
        BigDecimal.ZERO,
        "USDT");

    boolean takerRecorded = ledgerService.recordTrade(
        "TRD-SHARED",
        1002L,
        "BTC",
        new BigDecimal("-1"),
        "USDT",
        new BigDecimal("50000"),
        BigDecimal.ZERO,
        "USDT");

    assertThat(makerRecorded).isTrue();
    assertThat(takerRecorded).isTrue();
    verify(ledgerRepository, times(4)).save(any());
  }

  @Test
  @DisplayName("특정 계정 자산 조회")
  void testFindByAccountIdAndAsset() {
    // Given
    LedgerEntry entry1 = LedgerEntry.builder()
        .id(1L)
        .accountId(1001L)
        .asset("BTC")
        .amount(new BigDecimal("0.1"))
        .entryType(EntryType.TRADE)
        .idempotencyKey("KEY-1")
        .build();

    LedgerEntry entry2 = LedgerEntry.builder()
        .id(2L)
        .accountId(1001L)
        .asset("BTC")
        .amount(new BigDecimal("-0.05"))
        .entryType(EntryType.FEE)
        .idempotencyKey("KEY-2")
        .build();

    when(ledgerRepository.findByAccountIdAndAssetOrderByCreatedAtDesc(1001L, "BTC"))
        .thenReturn(List.of(entry1, entry2));

    // When
    List<LedgerEntry> results = ledgerRepository
        .findByAccountIdAndAssetOrderByCreatedAtDesc(1001L, "BTC");

    // Then
    assertThat(results).hasSize(2);
    assertThat(results.get(0).getAmount()).isEqualByComparingTo("0.1");
    assertThat(results.get(1).getAmount()).isEqualByComparingTo("-0.05");
  }

  @Test
  @DisplayName("멱등성 키 존재 확인")
  void testExistsByIdempotencyKey() {
    // Given
    when(ledgerRepository.existsByIdempotencyKey("KEY-EXISTS")).thenReturn(true);
    when(ledgerRepository.existsByIdempotencyKey("KEY-NOT-EXISTS")).thenReturn(false);

    // When & Then
    assertThat(ledgerRepository.existsByIdempotencyKey("KEY-EXISTS")).isTrue();
    assertThat(ledgerRepository.existsByIdempotencyKey("KEY-NOT-EXISTS")).isFalse();
  }

  @Test
  @DisplayName("거래 ID로 원장 조회")
  void testFindByTradeId() {
    // Given
    LedgerEntry entry1 = LedgerEntry.builder()
        .id(1L)
        .tradeId("TRD-001")
        .accountId(1001L)
        .asset("BTC")
        .amount(new BigDecimal("0.1"))
        .entryType(EntryType.TRADE)
        .build();

    LedgerEntry entry2 = LedgerEntry.builder()
        .id(2L)
        .tradeId("TRD-001")
        .accountId(1001L)
        .asset("USDT")
        .amount(new BigDecimal("-5000"))
        .entryType(EntryType.TRADE)
        .build();

    LedgerEntry entry3 = LedgerEntry.builder()
        .id(3L)
        .tradeId("TRD-001")
        .accountId(1001L)
        .asset("USDT")
        .amount(new BigDecimal("-5"))
        .entryType(EntryType.FEE)
        .build();

    when(ledgerRepository.findByTradeId("TRD-001"))
        .thenReturn(List.of(entry1, entry2, entry3));

    // When
    List<LedgerEntry> results = ledgerRepository.findByTradeId("TRD-001");

    // Then
    assertThat(results).hasSize(3);
    assertThat(results).extracting("tradeId").containsOnly("TRD-001");

    // 합계 검증 (BTC + USDT trade + FEE = 순자산 변동)
    BigDecimal btcSum = results.stream()
        .filter(e -> "BTC".equals(e.getAsset()))
        .map(LedgerEntry::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(btcSum).isEqualByComparingTo("0.1");

    BigDecimal usdtSum = results.stream()
        .filter(e -> "USDT".equals(e.getAsset()))
        .map(LedgerEntry::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(usdtSum).isEqualByComparingTo("-5005"); // -5000 - 5
  }

  @Test
  @DisplayName("LedgerEntry 빌더 테스트")
  void testLedgerEntryBuilder() {
    // When
    LedgerEntry entry = LedgerEntry.builder()
        .id(1L)
        .tradeId("TRD-001")
        .accountId(1001L)
        .asset("BTC")
        .amount(new BigDecimal("0.1"))
        .entryType(EntryType.TRADE)
        .idempotencyKey("KEY-1")
        .build();

    // Then
    assertThat(entry.getId()).isEqualTo(1L);
    assertThat(entry.getTradeId()).isEqualTo("TRD-001");
    assertThat(entry.getAccountId()).isEqualTo(1001L);
    assertThat(entry.getAsset()).isEqualTo("BTC");
    assertThat(entry.getAmount()).isEqualByComparingTo("0.1");
    assertThat(entry.getEntryType()).isEqualTo(EntryType.TRADE);
    assertThat(entry.getIdempotencyKey()).isEqualTo("KEY-1");
  }
}
