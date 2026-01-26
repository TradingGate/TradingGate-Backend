package org.tradinggate.backend.risk.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TradeExecutedEvent {

  @JsonProperty("tradeId")
  private Long tradeId;

  @JsonProperty("userId")
  private Long userId;

  @JsonProperty("symbol")
  private String symbol; // "BTCUSDT"

  @JsonProperty("side")
  private String side; // "BUY" or "SELL"

  @JsonProperty("execQuantity")
  private BigDecimal execQuantity;

  @JsonProperty("execPrice")
  private BigDecimal execPrice;

  @JsonProperty("execValue")
  private BigDecimal execValue;

  @JsonProperty("feeAmount")
  private BigDecimal feeAmount;

  @JsonProperty("feeCurrency")
  private String feeCurrency;

  @JsonProperty("liquidityFlag")
  private String liquidityFlag; // "MAKER" or "TAKER"

  @JsonProperty("execTime")
  private LocalDateTime execTime;

  // accountId와 symbolId는 symbol에서 파싱
  public Long getAccountId() {
    return userId;
  }

  public BigDecimal getQuantity() {
    // BUY면 +, SELL이면 -
    return "BUY".equals(side) ? execQuantity : execQuantity.negate();
  }

  public BigDecimal getPrice() {
    return execPrice;
  }
}
