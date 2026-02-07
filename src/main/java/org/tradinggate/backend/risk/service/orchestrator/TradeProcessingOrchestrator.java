package org.tradinggate.backend.risk.service.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
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
@Profile("risk")
@RequiredArgsConstructor
public class TradeProcessingOrchestrator {

  private final LedgerService ledgerService;
  private final BalanceService balanceService;
  private final RiskCheckService riskCheckService;

  // 체결 이벤트 처리
  @Transactional
  public boolean processTrade(TradeExecutedEvent event) {
    log.info("체결 처리 중: tradeId={}, accountId={}, symbol={}, side={}, qty={}, price={}",
        event.getTradeId(), event.getAccountId(), event.getSymbol(),
        event.getSide(), event.getQuantity(), event.getPrice());

    try {
      boolean recorded = recordToLedger(event);
      if (!recorded) {
        log.info("⚠️ Duplicate trade event ignored: tradeId={}", event.getTradeId());
        return true; // 멱등성 보장
      }
      updateBalance(event);
      checkRisk(event);
      log.info("체결 완료: tradeId={}", event.getTradeId());
      return true;
    } catch (Exception e) {
      log.error("체결 실패: tradeId={}, error={}",
          event.getTradeId(), e.getMessage(), e);
      // 트랜잭션 롤백
      throw new RuntimeException("채결 실패", e);
    }
  }

  // 1. 원장 기록
  private boolean recordToLedger(TradeExecutedEvent event) {
    log.debug("원장 기록: tradeId={}", event.getTradeId());
    String baseAsset = event.getBaseAsset();
    String quoteAsset = event.getQuoteAsset();
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
      log.debug("원장 기록: tradeId={}", event.getTradeId());
    }
    return recorded;
  }

  // 2. 잔고 업데이트
  private void updateBalance(TradeExecutedEvent event) {
    log.debug("잔고 업데이트 중: accountId={}", event.getAccountId());
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
    balanceService.updateBalances(event.getAccountId(), changes);
    log.debug("잔고 업데이트 완료: accountId={}, changes={}",
        event.getAccountId(), changes);
  }

  // 3. 잔고 체크
  private void checkRisk(TradeExecutedEvent event) {
    log.debug("🔍 Checking risk: accountId={}", event.getAccountId());
    // 계정의 모든 자산 잔고 음수 체크
    riskCheckService.checkNegativeBalance(event.getAccountId());
    log.debug("리스크 체크 완료: accountId={}", event.getAccountId());
  }
}
