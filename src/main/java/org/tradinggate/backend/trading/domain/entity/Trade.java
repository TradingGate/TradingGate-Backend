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
 *
 * 역할:
 * - Trading DB trading_trade 테이블 매핑
 * - Projection Consumer가 trades.executed 이벤트를 받아 저장
 *
 * TODO:
 * [ ] 필드 확인 (PDF 스키마 기준):
 *     - Long id (PK)
 *     - Long tradeId (매칭 엔진이 발행한 체결 ID)
 *     - Long matchId (매칭 단위 ID)
 *     - Long orderId
 *     - Long userId
 *     - String symbol
 *     - OrderSide side (BUY/SELL)
 *     - BigDecimal execQuantity
 *     - BigDecimal execPrice
 *     - BigDecimal execValue (qty * price)
 *     - BigDecimal feeAmount
 *     - String feeCurrency
 *     - String liquidityFlag (MAKER/TAKER)
 *     - LocalDateTime execTime
 *
 * [ ] JPA 어노테이션:
 *     - @Entity
 *     - @Table(name = "trading_trade")
 *     - UNIQUE: tradeId
 *
 * [ ] 인덱스:
 *     - (tradeId)
 *     - (orderId)
 *     - (userId, execTime DESC)
 *
 * 참고: PDF 3 (trading_trade 테이블 구조)
 */
@Entity
@Table(
    name = "trading_trade",
    indexes = {
        @Index(name = "idx_trade_id", columnList = "trade_id", unique = true),
        @Index(name = "idx_order_id", columnList = "order_id"),
        @Index(name = "idx_user_exec_time", columnList = "user_id, exec_time DESC")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Trade extends Timestamped {

  // =====================================
  // 필드 정의
  // =====================================

  /**
   * DB Auto Increment PK
   */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * 매칭 엔진이 발행한 체결 ID (비즈니스 식별자)
   * - UNIQUE 제약 조건으로 멱등성 보장
   */
  @Column(name = "trade_id", nullable = false, unique = true)
  private Long tradeId;

  /**
   * 매칭 단위 ID
   * - 하나의 매칭에서 여러 체결이 발생할 수 있음
   */
  @Column(name = "match_id", nullable = false)
  private Long matchId;

  /**
   * 주문 ID
   */
  @Column(name = "order_id", nullable = false)
  private Long orderId;

  /**
   * 사용자 ID
   */
  @Column(name = "user_id", nullable = false)
  private Long userId;

  /**
   * 거래 심볼 (BTCUSDT, ETHUSDT 등)
   */
  @Column(name = "symbol", nullable = false, length = 20)
  private String symbol;

  /**
   * 주문 방향 (BUY/SELL)
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "side", nullable = false, length = 10)
  private OrderSide side;

  /**
   * 체결 수량
   */
  @Column(name = "exec_quantity", nullable = false, precision = 20, scale = 8)
  private BigDecimal execQuantity;

  /**
   * 체결 가격
   */
  @Column(name = "exec_price", nullable = false, precision = 20, scale = 8)
  private BigDecimal execPrice;

  /**
   * 체결 금액 (execQuantity * execPrice)
   */
  @Column(name = "exec_value", nullable = false, precision = 20, scale = 8)
  private BigDecimal execValue;

  /**
   * 수수료
   */
  @Column(name = "fee_amount", nullable = false, precision = 20, scale = 8)
  private BigDecimal feeAmount;

  /**
   * 수수료 통화 (BTC, USDT 등)
   */
  @Column(name = "fee_currency", nullable = false, length = 10)
  private String feeCurrency;

  /**
   * 유동성 제공자 여부
   * - MAKER: 주문장에 주문을 등록하여 유동성 제공
   * - TAKER: 기존 주문을 체결하여 유동성 소비
   */
  @Column(name = "liquidity_flag", nullable = false, length = 10)
  private String liquidityFlag;

  /**
   * 체결 시각 (매칭 엔진이 체결한 시각)
   */
  @Column(name = "exec_time", nullable = false)
  private LocalDateTime execTime;

  // =====================================
  // 정적 팩토리 메서드 (생성 책임)
  // =====================================

  /**
   * 체결 생성
   * - Kafka 이벤트(trades.executed)에서 받은 데이터로 생성
   *
   * @param tradeId 매칭 엔진 체결 ID
   * @param matchId 매칭 단위 ID
   * @param orderId 주문 ID
   * @param userId 사용자 ID
   * @param symbol 거래 심볼
   * @param side 주문 방향
   * @param execQuantity 체결 수량
   * @param execPrice 체결 가격
   * @param feeAmount 수수료
   * @param feeCurrency 수수료 통화
   * @param liquidityFlag MAKER/TAKER
   * @param execTime 체결 시각
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
      LocalDateTime execTime
  ) {
    // 체결 금액 계산 (quantity * price)
    BigDecimal execValue = execQuantity.multiply(execPrice);

    return new Trade(
        null,  // id는 DB에서 자동 생성
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
        execTime
    );
  }

  // =====================================
  // 비즈니스 메서드
  // =====================================

  /**
   * MAKER 체결 여부 확인
   */
  public boolean isMaker() {
    return "MAKER".equals(this.liquidityFlag);
  }

  /**
   * TAKER 체결 여부 확인
   */
  public boolean isTaker() {
    return "TAKER".equals(this.liquidityFlag);
  }

  /**
   * 매수 체결 여부
   */
  public boolean isBuy() {
    return this.side == OrderSide.BUY;
  }

  /**
   * 매도 체결 여부
   */
  public boolean isSell() {
    return this.side == OrderSide.SELL;
  }

  /**
   * 체결 정보 문자열 표현
   */
  @Override
  public String toString() {
    return String.format(
        "Trade[tradeId=%d, orderId=%d, symbol=%s, side=%s, qty=%s, price=%s, flag=%s]",
        tradeId, orderId, symbol, side, execQuantity, execPrice, liquidityFlag
    );
  }
}