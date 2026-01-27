package org.tradinggate.backend.risk.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * trades.executed 원본 데이터 저장
 */
@Entity
@Getter
@Table(
    name = "processed_trade",
    indexes = {
        @Index(name = "idx_trade_id", columnList = "tradeId", unique = true),
        @Index(name = "idx_account_exec_time", columnList = "accountId, execTime")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedTrade {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private Long tradeId;

  // B-6 Reconciliation을 위한 추가 필드
  @Column(nullable = false)
  private Long accountId;

  @Column(nullable = false)
  private Long symbolId;

  @Column(nullable = false, length = 32)
  private String symbol;

  @Column(nullable = false, length = 10)
  private String side; // "BUY" or "SELL"

  @Column(nullable = false, precision = 36, scale = 18)
  private BigDecimal execQuantity;

  @Column(nullable = false, precision = 18, scale = 8)
  private BigDecimal execPrice;

  @Column(nullable = false, precision = 36, scale = 18)
  private BigDecimal execValue;

  @Column(nullable = false, precision = 18, scale = 8)
  private BigDecimal feeAmount;

  @Column(length = 16)
  private String feeCurrency;

  @Column(nullable = false)
  private LocalDateTime execTime;

  @Column(nullable = false)
  private LocalDateTime processedAt;

  public ProcessedTrade(Long tradeId, Long accountId, Long symbolId, String symbol,
                        String side, BigDecimal execQuantity, BigDecimal execPrice,
                        BigDecimal execValue, BigDecimal feeAmount, String feeCurrency,
                        LocalDateTime execTime) {
    this.tradeId = tradeId;
    this.accountId = accountId;
    this.symbolId = symbolId;
    this.symbol = symbol;
    this.side = side;
    this.execQuantity = execQuantity;
    this.execPrice = execPrice;
    this.execValue = execValue;
    this.feeAmount = feeAmount;
    this.feeCurrency = feeCurrency;
    this.execTime = execTime;
    this.processedAt = LocalDateTime.now();
  }
}
