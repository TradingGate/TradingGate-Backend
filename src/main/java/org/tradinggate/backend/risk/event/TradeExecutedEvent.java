package org.tradinggate.backend.risk.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeExecutedEvent {
  private Long accountId;
  private Long symbolId;
  private BigDecimal quantity;
  private BigDecimal price;
  private LocalDateTime tradeTime;
}
