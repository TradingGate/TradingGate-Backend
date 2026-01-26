package org.tradinggate.backend.risk.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 포지션 업데이트 결과 DTO
 */
@Getter
@AllArgsConstructor
public class PositionUpdateResult {
  private final BigDecimal newQuantity;
  private final BigDecimal newAvgPrice;
  private final BigDecimal realizedPnl;
  private final BigDecimal unrealizedPnl;
}
