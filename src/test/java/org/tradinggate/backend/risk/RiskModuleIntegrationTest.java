package org.tradinggate.backend.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.risk.domain.entity.balance.AccountBalance;
import org.tradinggate.backend.risk.domain.entity.ledger.LedgerEntry;
import org.tradinggate.backend.risk.domain.entity.risk.RiskState;
import org.tradinggate.backend.risk.domain.entity.risk.RiskStatus;
import org.tradinggate.backend.risk.kafka.dto.TradeExecutedEvent;
import org.tradinggate.backend.risk.repository.balance.AccountBalanceRepository;
import org.tradinggate.backend.risk.repository.ledger.LedgerEntryRepository;
import org.tradinggate.backend.risk.repository.risk.RiskStateRepository;
import org.tradinggate.backend.risk.service.orchestrator.TradeProcessingOrchestrator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Risk 모듈 통합 테스트
 *
 * 전체 흐름:
 * - Orchestrator → Ledger → Balance → Risk
 * - 멱등성, 잔고 음수 자동 블락 등
 */
@SpringBootTest
@ActiveProfiles("risk")
@EmbeddedKafka(partitions = 1, topics = {"trades.executed"})
@Transactional
class RiskModuleIntegrationTest {

  @Autowired
  private TradeProcessingOrchestrator orchestrator;

  @Autowired
  private LedgerEntryRepository ledgerRepository;

  @Autowired
  private AccountBalanceRepository balanceRepository;

  @Autowired
  private RiskStateRepository riskStateRepository;

  @Autowired
  private ObjectMapper objectMapper;

  private static final Long TEST_ACCOUNT_ID = 1001L;

  @BeforeEach
  void setUp() {
    // 초기 잔고 설정
    createBalance(TEST_ACCOUNT_ID, "BTC", BigDecimal.ZERO);
    createBalance(TEST_ACCOUNT_ID, "USDT", new BigDecimal("100000"));
  }

  @Test
  @DisplayName("통합: BTC 매수 - 전체 흐름")
  void testBuyFlow() {
    TradeExecutedEvent event = createBuyEvent("TRD-001", "0.1", "50000", "5");

    boolean result = orchestrator.processTrade(event);

    assertThat(result).isTrue();

    // 1. 원장 검증
    List<LedgerEntry> btcLedger = ledgerRepository.findByAccountIdAndAssetOrderByCreatedAtDesc(TEST_ACCOUNT_ID, "BTC");
    assertThat(btcLedger).hasSize(1);
    assertThat(btcLedger.get(0).getAmount()).isEqualByComparingTo("0.1");

    List<LedgerEntry> usdtLedger = ledgerRepository.findByAccountIdAndAssetOrderByCreatedAtDesc(TEST_ACCOUNT_ID, "USDT");
    assertThat(usdtLedger).hasSize(2);  // TRADE + FEE

    // 2. 잔고 검증
    assertBalance(TEST_ACCOUNT_ID, "BTC", "0.1");
    assertBalance(TEST_ACCOUNT_ID, "USDT", "94995");

    // 3. 리스크 상태 검증
    Optional<RiskState> riskState = riskStateRepository.findById(TEST_ACCOUNT_ID);
    if (riskState.isPresent()) {
      assertThat(riskState.get().getStatus()).isEqualTo(RiskStatus.NORMAL);
    } else {
      assertThat(riskState).isEmpty();
    }
  }

  @Test
  @DisplayName("통합: BTC 매도 - 전체 흐름")
  void testSellFlow() {
    createBalance(TEST_ACCOUNT_ID, "BTC", new BigDecimal("1.0"));
    TradeExecutedEvent event = createSellEvent("TRD-002", "0.5", "50000", "25");

    boolean result = orchestrator.processTrade(event);

    assertThat(result).isTrue();
    assertBalance(TEST_ACCOUNT_ID, "BTC", "0.5");
    assertBalance(TEST_ACCOUNT_ID, "USDT", "124975");
  }


  @Test
  @DisplayName("통합: 중복 이벤트 멱등성")
  void testIdempotency() {
    // Given
    TradeExecutedEvent event = createBuyEvent("TRD-SAME", "0.1", "50000", "5");

    // When: 첫 번째 처리
    orchestrator.processTrade(event);
    BigDecimal btcAfter1 = getBalance(TEST_ACCOUNT_ID, "BTC");
    BigDecimal usdtAfter1 = getBalance(TEST_ACCOUNT_ID, "USDT");

    // When: 두 번째 처리 (중복)
    orchestrator.processTrade(event);
    BigDecimal btcAfter2 = getBalance(TEST_ACCOUNT_ID, "BTC");
    BigDecimal usdtAfter2 = getBalance(TEST_ACCOUNT_ID, "USDT");

    // Then: 잔고 변동 없음
    assertThat(btcAfter2).isEqualByComparingTo(btcAfter1);
    assertThat(usdtAfter2).isEqualByComparingTo(usdtAfter1);

    // 원장도 중복 없음
    List<LedgerEntry> btcLedger = ledgerRepository.findByAccountIdAndAssetOrderByCreatedAtDesc(TEST_ACCOUNT_ID, "BTC");
    assertThat(btcLedger).hasSize(1);
  }

  @Test
  @DisplayName("통합: 잔고 부족 시 자동 블락")
  void testNegativeBalanceAutoBlock() {
    // Given: ETH 0.5개만 보유
    createBalance(TEST_ACCOUNT_ID, "ETH", new BigDecimal("0.5"));

    // When: ETH 1.0개 매도 시도
    TradeExecutedEvent event = TradeExecutedEvent.builder()
        .tradeId("TRD-NEG")
        .accountId(TEST_ACCOUNT_ID)
        .symbol("ETHUSDT")
        .side("SELL")
        .quantity(new BigDecimal("1.0"))
        .price(new BigDecimal("3000"))
        .fee(new BigDecimal("3"))
        .feeAsset("USDT")
        .executedAt(LocalDateTime.now())
        .build();

    orchestrator.processTrade(event);
    assertBalance(TEST_ACCOUNT_ID, "ETH", "-0.5");

    RiskState riskState = riskStateRepository.findById(TEST_ACCOUNT_ID).orElseThrow();
    assertThat(riskState.getStatus()).isEqualTo(RiskStatus.BLOCKED);
    assertThat(riskState.getBlockReason()).contains("ETH").contains("-0.5");
  }

  @Test
  @DisplayName("통합: 여러 자산 동시 거래")
  void testMultipleAssets() {
    // Given
    createBalance(TEST_ACCOUNT_ID, "BTC", new BigDecimal("2.0"));
    createBalance(TEST_ACCOUNT_ID, "ETH", new BigDecimal("20.0"));

    // When: BTC 매도
    orchestrator.processTrade(createSellEvent("TRD-BTC", "1.0", "50000", "50"));

    // When: ETH 매도
    TradeExecutedEvent ethEvent = TradeExecutedEvent.builder()
        .tradeId("TRD-ETH")
        .accountId(TEST_ACCOUNT_ID)
        .symbol("ETHUSDT")
        .side("SELL")
        .quantity(new BigDecimal("10.0"))
        .price(new BigDecimal("3000"))
        .fee(new BigDecimal("30"))
        .feeAsset("USDT")
        .executedAt(LocalDateTime.now())
        .build();
    orchestrator.processTrade(ethEvent);

    // Then
    assertBalance(TEST_ACCOUNT_ID, "BTC", "1.0");
    assertBalance(TEST_ACCOUNT_ID, "ETH", "10.0");
    // 100000 + 50000 - 50 + 30000 - 30 = 179920
    assertBalance(TEST_ACCOUNT_ID, "USDT", "179920");
  }

  @Test
  @DisplayName("통합: 연속 거래 10회")
  void testMultipleTrades() {
    // Given
    createBalance(TEST_ACCOUNT_ID, "BTC", new BigDecimal("10.0"));

    // When: 10회 매도
    for (int i = 1; i <= 10; i++) {
      TradeExecutedEvent event = createSellEvent(
          "TRD-MULTI-" + i,
          "0.1",
          "50000",
          "5"
      );
      orchestrator.processTrade(event);
    }

    assertBalance(TEST_ACCOUNT_ID, "BTC", "9.0");
    // 100000 + (5000 - 5) * 10 = 149950
    assertBalance(TEST_ACCOUNT_ID, "USDT", "149950");

    // 원장 확인
    List<LedgerEntry> btcLedger = ledgerRepository.findByAccountIdAndAssetOrderByCreatedAtDesc(TEST_ACCOUNT_ID, "BTC");
    assertThat(btcLedger).hasSize(10);
  }

  @Test
  @DisplayName("통합: 같은 tradeId라도 서로 다른 계정의 maker/taker는 각각 반영된다")
  void testSameTradeIdDifferentAccountsAreRecordedSeparately() {
    Long makerAccountId = 4001L;
    Long takerAccountId = 4002L;

    createBalance(makerAccountId, "BTC", BigDecimal.ZERO);
    createBalance(makerAccountId, "USDT", BigDecimal.ZERO);
    createBalance(takerAccountId, "BTC", BigDecimal.ZERO);
    createBalance(takerAccountId, "USDT", BigDecimal.ZERO);

    TradeExecutedEvent maker = TradeExecutedEvent.builder()
        .tradeId("TRD-SHARED")
        .accountId(makerAccountId)
        .symbol("BTCUSDT")
        .side("BUY")
        .quantity(new BigDecimal("1"))
        .price(new BigDecimal("50000"))
        .fee(BigDecimal.ZERO)
        .feeAsset("USDT")
        .executedAt(LocalDateTime.now())
        .build();

    TradeExecutedEvent taker = TradeExecutedEvent.builder()
        .tradeId("TRD-SHARED")
        .accountId(takerAccountId)
        .symbol("BTCUSDT")
        .side("SELL")
        .quantity(new BigDecimal("1"))
        .price(new BigDecimal("50000"))
        .fee(BigDecimal.ZERO)
        .feeAsset("USDT")
        .executedAt(LocalDateTime.now())
        .build();

    assertThat(orchestrator.processTrade(maker)).isTrue();
    assertThat(orchestrator.processTrade(taker)).isTrue();

    assertBalance(makerAccountId, "BTC", "1");
    assertBalance(makerAccountId, "USDT", "-50000");
    assertBalance(takerAccountId, "BTC", "-1");
    assertBalance(takerAccountId, "USDT", "50000");

    List<LedgerEntry> entries = ledgerRepository.findByTradeId("TRD-SHARED");
    assertThat(entries).hasSize(4);
    assertThat(entries).extracting(LedgerEntry::getAccountId).containsExactlyInAnyOrder(
        makerAccountId, makerAccountId, takerAccountId, takerAccountId
    );
  }

  private TradeExecutedEvent createBuyEvent(String tradeId, String qty, String price, String fee) {
    return TradeExecutedEvent.builder()
        .tradeId(tradeId)
        .accountId(TEST_ACCOUNT_ID)
        .symbol("BTCUSDT")
        .side("BUY")
        .quantity(new BigDecimal(qty))
        .price(new BigDecimal(price))
        .fee(new BigDecimal(fee))
        .feeAsset("USDT")
        .executedAt(LocalDateTime.now())
        .build();
  }

  private TradeExecutedEvent createSellEvent(String tradeId, String qty, String price, String fee) {
    return TradeExecutedEvent.builder()
        .tradeId(tradeId)
        .accountId(TEST_ACCOUNT_ID)
        .symbol("BTCUSDT")
        .side("SELL")
        .quantity(new BigDecimal(qty))
        .price(new BigDecimal(price))
        .fee(new BigDecimal(fee))
        .feeAsset("USDT")
        .executedAt(LocalDateTime.now())
        .build();
  }

  private void createBalance(Long accountId, String asset, BigDecimal amount) {
    AccountBalance balance = AccountBalance.builder()
        .accountId(accountId)
        .asset(asset)
        .available(amount)
        .locked(BigDecimal.ZERO)
        .build();
    balanceRepository.save(balance);
  }

  private void assertBalance(Long accountId, String asset, String expected) {
    BigDecimal actual = getBalance(accountId, asset);
    assertThat(actual).isEqualByComparingTo(expected);
  }

  private BigDecimal getBalance(Long accountId, String asset) {
    return balanceRepository.findByAccountIdAndAsset(accountId, asset)
        .map(AccountBalance::getAvailable)
        .orElse(BigDecimal.ZERO);
  }
}
