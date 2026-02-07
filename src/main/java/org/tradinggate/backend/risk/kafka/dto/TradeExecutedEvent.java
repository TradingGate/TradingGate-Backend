package org.tradinggate.backend.risk.kafka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeExecutedEvent {

  @JsonProperty("trade_id")
  private String tradeId;

  @JsonProperty("account_id")
  private Long accountId;

  @JsonProperty("symbol")
  private String symbol;

  @JsonProperty("side")
  private String side; // BUY, SELL

  @JsonProperty("quantity")
  private BigDecimal quantity;

  @JsonProperty("price")
  private BigDecimal price;

  @JsonProperty("fee")
  private BigDecimal fee;

  @JsonProperty("fee_asset")
  private String feeAsset;

  @JsonProperty("executed_at")
  private LocalDateTime executedAt;

  /**
   * Base Asset 추출 (예: BTCUSDT → BTC)
   */
  public String getBaseAsset() {
    if (symbol.endsWith("USDT")) {
      return symbol.replace("USDT", "");
    } else if (symbol.endsWith("USDC")) {
      return symbol.replace("USDC", "");
    } else if (symbol.endsWith("USD")) {
      return symbol.replace("USD", "");
    }
    // 기본: 앞 3자리
    return symbol.length() >= 3 ? symbol.substring(0, 3) : symbol;
  }

  /**
   * Quote Asset 추출 (예: BTCUSDT → USDT)
   */
  public String getQuoteAsset() {
    if (symbol.endsWith("USDT")) {
      return "USDT";
    } else if (symbol.endsWith("USDC")) {
      return "USDC";
    } else if (symbol.endsWith("USD")) {
      return "USD";
    }
    // 기본: 뒤 4자리
    return symbol.length() >= 4 ? symbol.substring(symbol.length() - 4) : symbol;
  }

  /**
   * Base Asset 변동량 계산
   * - BUY: +quantity
   * - SELL: -quantity
   */
  public BigDecimal getBaseAssetChange() {
    return "BUY".equalsIgnoreCase(side) ? quantity : quantity.negate();
  }

  /**
   * Quote Asset 변동량 계산
   * - BUY: -(quantity * price)
   * - SELL: +(quantity * price)
   */
  public BigDecimal getQuoteAssetChange() {
    BigDecimal amount = quantity.multiply(price);
    return "BUY".equalsIgnoreCase(side) ? amount.negate() : amount;
  }
}
