package org.tradinggate.backend.trading.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.tradinggate.backend.global.base.Timestamped;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * [A-1] Trading API - 체결 Entity
 * 역할:
 * - Trading DB trading_trade 테이블 매핑
 * - Projection Consumer가 trades.executed 이벤트를 받아 저장
 */
@Entity
@Table(name = "trading_trade", indexes = {
    @Index(name = "idx_trade_id", columnList = "trade_id", unique = true),
    @Index(name = "idx_order_id", columnList = "order_id"),
    @Index(name = "idx_user_exec_time", columnList = "user_id, exec_time DESC")
})

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Trade extends Timestamped {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "trade_id", nullable = false, unique = true)
  private Long tradeId;

  @Column(name = "match_id", nullable = false)
  private Long matchId;

  @Column(name = "order_id", nullable = false)
  private Long orderId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "symbol", nullable = false, length = 20)
  private String symbol;

  @Enumerated(EnumType.STRING)
  @Column(name = "side", nullable = false, length = 10)
  private OrderSide side;

  @Column(name = "exec_quantity", nullable = false, precision = 20, scale = 8)
  private BigDecimal execQuantity;

  @Column(name = "exec_price", nullable = false, precision = 20, scale = 8)
  private BigDecimal execPrice;

  @Column(name = "exec_value", nullable = false, precision = 20, scale = 8)
  private BigDecimal execValue;

  @Column(name = "fee_amount", nullable = false, precision = 20, scale = 8)
  private BigDecimal feeAmount;

  @Column(name = "fee_currency", nullable = false, length = 10)
  private String feeCurrency;

  @Column(name = "liquidity_flag", nullable = false, length = 10)
  private String liquidityFlag;

  @Column(name = "exec_time", nullable = false)
  private LocalDateTime execTime;

  // =====================================
  // 정적 팩토리 메서드 (생성 책임)
  // =====================================

  /**
   * 체결 생성
   * - Kafka 이벤트(trades.executed)에서 받은 데이터로 생성
   *
   * @param tradeId       매칭 엔진 체결 ID
   * @param matchId       매칭 단위 ID
   * @param orderId       주문 ID
   * @param userId        사용자 ID
   * @param symbol        거래 심볼
   * @param side          주문 방향
   * @param execQuantity  체결 수량
   * @param execPrice     체결 가격
   * @param feeAmount     수수료
   * @param feeCurrency   수수료 통화
   * @param liquidityFlag MAKER/TAKER
   * @param execTime      체결 시각
   * @return Trade 인스턴스
   */
  public static Trade create(
      Long tradeId,
      Long matchId,
      Long orderId,
      Long userId,
      String symbol,
      OrderSide side,
      BigDecimal execQuantity,
      BigDecimal execPrice,
      BigDecimal feeAmount,
      String feeCurrency,
      String liquidityFlag,
      LocalDateTime execTime) {
    // 체결 금액 계산 (quantity * price)
    BigDecimal execValue = execQuantity.multiply(execPrice);

    return new Trade(
        null, // id는 DB에서 자동 생성
        tradeId,
        matchId,
        orderId,
        userId,
        symbol,
        side,
        execQuantity,
        execPrice,
        execValue,
        feeAmount,
        feeCurrency,
        liquidityFlag,
        execTime);
  }

  // =====================================
  // 비즈니스 메서드
  // =====================================

  public boolean isMaker() {
    return "MAKER".equals(this.liquidityFlag);
  }

  public boolean isTaker() {
    return "TAKER".equals(this.liquidityFlag);
  }

  public boolean isBuy() {
    return this.side == OrderSide.BUY;
  }

  public boolean isSell() {
    return this.side == OrderSide.SELL;
  }

  @Override
  public String toString() {
    return String.format(
        "Trade[tradeId=%d, orderId=%d, symbol=%s, side=%s, qty=%s, price=%s, flag=%s]",
        tradeId, orderId, symbol, side, execQuantity, execPrice, liquidityFlag);
  }
}