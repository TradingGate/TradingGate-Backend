package org.tradinggate.backend.risk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.risk.domain.entity.Position;
import org.tradinggate.backend.risk.domain.entity.PnlIntraday;
import org.tradinggate.backend.risk.domain.entity.ProcessedTrade;
import org.tradinggate.backend.risk.event.TradeExecutedEvent;
import org.tradinggate.backend.risk.repository.PositionRepository;
import org.tradinggate.backend.risk.repository.PnlIntradayRepository;
import org.tradinggate.backend.risk.repository.ProcessedTradeRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {

  private final PositionRepository positionRepository;
  private final PnlIntradayRepository pnlIntradayRepository;
  private final ProcessedTradeRepository processedTradeRepository;
  private final PnlService pnlService;
  private final AccountBalanceService accountBalanceService;
  private final SymbolService symbolService;
  private final RiskManagementService riskManagementService;

  /**
   * B-1: trades.executed 카프카 메시지 처리
   * 1. 포지션 업데이트
   * 2. PnL 계산
   * 3. pnlintraday 저장
   * 4. accountbalance 업데이트
   * 5. processed_trade 저장
   * 6. 리스크 체크
   */
  @Transactional
  public void processTradeExecution(TradeExecutedEvent event) {
    Long accountId = event.getAccountId();
    String symbol = event.getSymbol();
    Long tradeId = event.getTradeId();

    log.debug("Processing trade: accountId={}, symbol={}, tradeId={}",
        accountId, symbol, tradeId);

    // symbol -> symbolId 변환
    Long symbolId = symbolService.getSymbolId(symbol);

    // 1. 기존 포지션 조회 또는 생성
    Position position = positionRepository.findByAccountIdAndSymbolId(accountId, symbolId)
        .orElseGet(() -> Position.createDefault(accountId, symbolId));

    BigDecimal tradeQty = event.getQuantity(); // BUY는 +, SELL는 -
    BigDecimal tradePrice = event.getPrice();
    BigDecimal feeAmount = event.getFeeAmount();

    // 2. 포지션 방향 확인
    boolean isSameDirection = position.getQuantity().signum() == tradeQty.signum()
        || position.getQuantity().signum() == 0;

    BigDecimal realizedPnl = BigDecimal.ZERO;

    if (isSameDirection) {
      // 포지션 증가: 평단가 갱신 (가중평균)
      BigDecimal totalQty = position.getQuantity().add(tradeQty);
      if (totalQty.compareTo(BigDecimal.ZERO) != 0) {
        BigDecimal oldNotional = position.getQuantity().multiply(position.getAvgPrice());
        BigDecimal newNotional = tradeQty.multiply(tradePrice);
        BigDecimal newAvgPrice = oldNotional.add(newNotional)
            .divide(totalQty, 8, RoundingMode.HALF_UP);

        position.increasePosition(totalQty, newAvgPrice);
        log.debug("Position increased: qty={}, avgPrice={}", totalQty, newAvgPrice);
      }
    } else {
      // 포지션 감소: 실현 손익 발생
      BigDecimal closeQty = tradeQty.abs().min(position.getQuantity().abs());

      realizedPnl = pnlService.calculateRealizedPnl(
          closeQty, position.getAvgPrice(), tradePrice, position.getQuantity().signum()
      );

      position.decreasePosition(tradeQty, realizedPnl);
      log.debug("Position decreased: qty={}, realizedPnl={}", position.getQuantity(), realizedPnl);
    }

    // 3. 미실현 손익 계산 (현재가 = tradePrice로 가정)
    BigDecimal unrealizedPnl = BigDecimal.ZERO;
    if (position.getQuantity().compareTo(BigDecimal.ZERO) != 0) {
      unrealizedPnl = position.getQuantity()
          .multiply(tradePrice.subtract(position.getAvgPrice()));
    }
    position.updateValuation(tradePrice, unrealizedPnl);

    // 4. position 테이블 저장
    positionRepository.save(position);
    log.info("Position saved: accountId={}, symbolId={}, qty={}, avgPrice={}, realizedPnl={}",
        accountId, symbolId, position.getQuantity(), position.getAvgPrice(), position.getRealizedPnl());

    // 5. pnlintraday 테이블 저장
    savePnlIntraday(accountId, symbolId, realizedPnl, unrealizedPnl, feeAmount);

    // 6. accountbalance 업데이트
    accountBalanceService.updateBalance(event);

    // 7. processed_trade 저장
    processedTradeRepository.save(new ProcessedTrade(tradeId));
    log.debug("ProcessedTrade saved: tradeId={}", tradeId);

    // 리스크 체크
    riskManagementService.checkPositionLimit(accountId);
    riskManagementService.evaluateMargin(accountId);

    log.info("Trade processed successfully: tradeId={}, accountId={}, symbol={}, realizedPnl={}",
        tradeId, accountId, symbol, realizedPnl);
  }

  /**
   * pnlintraday 테이블에 당일 PnL 저장 (B-5 Clearing이 사용)
   */
  private void savePnlIntraday(Long accountId, Long symbolId,
                               BigDecimal realizedPnl, BigDecimal unrealizedPnl,
                               BigDecimal fee) {
    LocalDate businessDate = LocalDate.now();

    PnlIntraday pnlIntraday = pnlIntradayRepository
        .findByBusinessDateAndAccountIdAndSymbolId(businessDate, accountId, symbolId)
        .orElseGet(() -> PnlIntraday.create(businessDate, accountId, symbolId));

    pnlIntraday.addRealizedPnl(realizedPnl);
    pnlIntraday.setUnrealizedPnl(unrealizedPnl);
    pnlIntraday.addFee(fee);

    pnlIntradayRepository.save(pnlIntraday);
    log.debug("PnL intraday saved: businessDate={}, accountId={}, symbolId={}, realizedPnl={}, unrealizedPnl={}",
        businessDate, accountId, symbolId, realizedPnl, unrealizedPnl);
  }

  @Transactional
  public void updatePosition(TradeExecutedEvent event) {
    // 기존 메서드 호환성 유지
    processTradeExecution(event);
  }
}
