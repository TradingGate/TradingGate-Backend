package org.tradinggate.backend.trading.api.dto.response;

import lombok.Builder;
import lombok.Getter;
import org.tradinggate.backend.trading.domain.entity.OrderSide;
import org.tradinggate.backend.trading.domain.entity.Trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * [A-1] Trading API - 체결 응답 DTO
 *
 * 역할:
 * - API → 클라이언트 체결 정보 응답
 *
 * TODO:
 * [ ] Trade Entity → TradeResponse 변환 메서드 추가
 *     - static TradeResponse from(Trade trade)
 *
 * [ ] 필드 정의:
 *     - tradeId, matchId, orderId, userId
 *     - symbol, side
 *     - execQuantity, execPrice, execValue
 *     - feeAmount, feeCurrency
 *     - liquidityFlag (MAKER/TAKER)
 *     - execTime
 *
 * 참고: PDF 3 (trading_trade 테이블 구조)
 */
@Getter
@Builder
public class TradeResponse {

  private Long id;
  private Long tradeId;
  private Long matchId;
  private Long orderId;
  private Long userId;
  private String symbol;
  private OrderSide side;
  private BigDecimal execQuantity;
  private BigDecimal execPrice;
  private BigDecimal execValue;
  private BigDecimal feeAmount;
  private String feeCurrency;
  private String liquidityFlag;
  private LocalDateTime execTime;
  private LocalDateTime createdAt;

  /**
   * Entity -> DTO 변환
   */
  public static TradeResponse from(Trade trade) {
    return TradeResponse.builder()
        .id(trade.getId())
        .tradeId(trade.getTradeId())
        .matchId(trade.getMatchId())
        .orderId(trade.getOrderId())
        .userId(trade.getUserId())
        .symbol(trade.getSymbol())
        .side(trade.getSide())
        .execQuantity(trade.getExecQuantity())
        .execPrice(trade.getExecPrice())
        .execValue(trade.getExecValue())
        .feeAmount(trade.getFeeAmount())
        .feeCurrency(trade.getFeeCurrency())
        .liquidityFlag(trade.getLiquidityFlag())
        .execTime(trade.getExecTime())
        .createdAt(trade.getCreatedAt())
        .build();
  }
}