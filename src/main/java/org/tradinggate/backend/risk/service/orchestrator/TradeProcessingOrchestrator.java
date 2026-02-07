package org.tradinggate.backend.risk.service.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.risk.kafka.dto.TradeExecutedEvent;
import org.tradinggate.backend.risk.service.balance.BalanceService;
import org.tradinggate.backend.risk.service.ledger.LedgerService;
import org.tradinggate.backend.risk.service.risk.RiskCheckService;
import java.util.HashMap;
import java.util.Map;
import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeProcessingOrchestrator {

  private final LedgerService ledgerService;
  private final BalanceService balanceService;
  private final RiskCheckService riskCheckService;

  /**
   * 체결 이벤트 처리 (메인 진입점)
   *
   * @param event 체결 이벤트
   * @return 처리 성공 여부
   */
  @Transactional
  public boolean processTrade(TradeExecutedEvent event) {
    log.info("🔄 Processing trade: tradeId={}, accountId={}, symbol={}, side={}, qty={}, price={}",
        event.getTradeId(), event.getAccountId(), event.getSymbol(),
        event.getSide(), event.getQuantity(), event.getPrice());

    try {
      // === STEP 1: 원장 기록 (B-1) ===
      boolean recorded = recordToLedger(event);

      if (!recorded) {
        // 중복 이벤트 (이미 처리됨)
        log.info("⚠️ Duplicate trade event ignored: tradeId={}", event.getTradeId());
        return true; // 멱등성 보장: 중복도 성공으로 처리
      }

      // === STEP 2: 잔고 업데이트===
      updateBalance(event);

      // === STEP 3: 리스크 체크 ===
      checkRisk(event);

      log.info("✅ Trade processed successfully: tradeId={}", event.getTradeId());
      return true;

    } catch (Exception e) {
      log.error("❌ Failed to process trade: tradeId={}, error={}",
          event.getTradeId(), e.getMessage(), e);

      // 트랜잭션 롤백 (Spring @Transactional이 자동 처리)
      throw new RuntimeException("Trade processing failed", e);
    }
  }

  /**
   * STEP 1: 원장 기록
   *
   * 예: BTC/USDT 매수
   * - BTC: +0.1 (TRADE)
   * - USDT: -1000 (TRADE)
   * - USDT: -1.0 (FEE)
   *
   * @return true: 새로 기록됨, false: 이미 존재함 (중복)
   */
  private boolean recordToLedger(TradeExecutedEvent event) {
    log.debug("📝 Recording to ledger: tradeId={}", event.getTradeId());

    String baseAsset = event.getBaseAsset();
    String quoteAsset = event.getQuoteAsset();

    // LedgerService.recordTrade()는 내부에서 3개 항목을 기록
    // 첫 번째 항목이 중복이면 전체 중복으로 간주
    boolean recorded = ledgerService.recordTrade(
        event.getTradeId(),
        event.getAccountId(),
        baseAsset,
        event.getBaseAssetChange(),
        quoteAsset,
        event.getQuoteAssetChange(),
        event.getFee() != null ? event.getFee() : BigDecimal.ZERO,
        event.getFeeAsset() != null ? event.getFeeAsset() : quoteAsset);

    if (recorded) {
      log.debug("✅ Ledger recorded: tradeId={}", event.getTradeId());
    }
    return recorded;
  }

  /**
   * STEP 2: 잔고 업데이트
   *
   * 원장 기록을 기반으로 잔고 projection 업데이트
   */
  private void updateBalance(TradeExecutedEvent event) {
    log.debug("💰 Updating balance: accountId={}", event.getAccountId());

    String baseAsset = event.getBaseAsset();
    String quoteAsset = event.getQuoteAsset();
    String feeAsset = event.getFeeAsset() != null ? event.getFeeAsset() : quoteAsset;

    // 자산별 변동량 계산
    Map<String, BigDecimal> changes = new HashMap<>();
    changes.put(baseAsset, event.getBaseAssetChange());
    changes.put(quoteAsset, event.getQuoteAssetChange());

    // 수수료 차감 (fee > 0인 경우)
    if (event.getFee() != null && event.getFee().compareTo(BigDecimal.ZERO) > 0) {
      changes.merge(feeAsset, event.getFee().negate(), BigDecimal::add);
    }

    // 잔고 일괄 업데이트
    balanceService.updateBalances(event.getAccountId(), changes);

    log.debug("✅ Balance updated: accountId={}, changes={}",
        event.getAccountId(), changes);
  }

  /**
   * STEP 3: 리스크 체크
   *
   * 잔고 음수 체크 → 발견 시 BalanceInsufficientEvent 발행
   * → RiskStateService가 @EventListener로 자동 처리
   */
  private void checkRisk(TradeExecutedEvent event) {
    log.debug("🔍 Checking risk: accountId={}", event.getAccountId());

    // 계정의 모든 자산 잔고 음수 체크
    riskCheckService.checkNegativeBalance(event.getAccountId());

    log.debug("✅ Risk check completed: accountId={}", event.getAccountId());
  }
}
