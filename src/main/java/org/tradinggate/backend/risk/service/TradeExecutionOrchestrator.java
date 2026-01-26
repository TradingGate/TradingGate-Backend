package org.tradinggate.backend.risk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.risk.api.dto.PositionUpdateResult;
import org.tradinggate.backend.risk.domain.entity.ProcessedTrade;
import org.tradinggate.backend.risk.event.TradeExecutedEvent;
import org.tradinggate.backend.risk.repository.ProcessedTradeRepository;

/**
 * B-1: trades.executed 처리의 오케스트레이터
 * - 각 서비스를 순서대로 호출하여 트레이드 실행 플로우 조율
 * - 트랜잭션 경계 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeExecutionOrchestrator {

  private final ProcessedTradeRepository processedTradeRepository;
  private final PositionService positionService;
  private final PnlIntradayService pnlIntradayService;
  private final AccountBalanceService accountBalanceService;
  private final RiskManagementService riskManagementService;
  private final SymbolService symbolService;

  /**
   * B-1: trades.executed 전체 플로우 실행
   */
  @Transactional
  public void executeTradeFlow(TradeExecutedEvent event) {
    Long accountId = event.getAccountId();
    String symbol = event.getSymbol();
    Long tradeId = event.getTradeId();

    log.debug("Executing trade flow: accountId={}, symbol={}, tradeId={}",
        accountId, symbol, tradeId);

    // 1. Symbol ID 조회
    Long symbolId = symbolService.getSymbolId(symbol);

    // 2. 포지션 업데이트 및 PnL 계산
    PositionUpdateResult result = positionService.updatePosition(
        accountId, symbolId, event.getQuantity(), event.getPrice()
    );

    log.info("Position updated: accountId={}, symbolId={}, qty={}, avgPrice={}, realizedPnl={}",
        accountId, symbolId, result.getNewQuantity(), result.getNewAvgPrice(), result.getRealizedPnl());

    // 3. PnL Intraday 저장 (B-5 Clearing이 사용)
    pnlIntradayService.savePnlIntraday(
        accountId, symbolId,
        result.getRealizedPnl(),
        result.getUnrealizedPnl(),
        event.getFeeAmount()
    );

    // 4. Account Balance 업데이트
    accountBalanceService.updateBalance(event);

    // 5. Processed Trade 저장 (멱등성 보장)
    ProcessedTrade processedTrade = new ProcessedTrade(
        tradeId,
        accountId,
        symbolId,
        symbol,
        event.getSide(),
        event.getExecQuantity(),
        event.getExecPrice(),
        event.getExecValue(),
        event.getFeeAmount(),
        event.getFeeCurrency(),
        event.getExecTime()
    );
    processedTradeRepository.save(processedTrade);
    log.debug("ProcessedTrade saved with full data: tradeId={}, accountId={}, symbol={}",
        tradeId, accountId, symbol);

    // 6. Risk 체크 (B-2, B-3)
    riskManagementService.checkPositionLimit(accountId);
    riskManagementService.evaluateMargin(accountId);

    log.info("Trade flow completed: tradeId={}, accountId={}, realizedPnl={}",
        tradeId, accountId, result.getRealizedPnl());
  }
}
