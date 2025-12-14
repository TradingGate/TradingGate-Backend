package org.tradinggate.backend.trading.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.tradinggate.backend.global.base.Timestamped;
import org.tradinggate.backend.global.exception.CustomException;
import org.tradinggate.backend.global.exception.DomainErrorCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * [A-1] Trading API - 주문 Entity
 * 역할:
 * - Trading DB trading_order 테이블 매핑
 * - Projection Consumer가 orders.updated 이벤트를 받아 저장
 */

@Entity
@Table(name = "trading_order", indexes = {
    @Index(name = "idx_order_id", columnList = "order_id"),
    @Index(name = "idx_user_created", columnList = "user_id, created_at"),
    @Index(name = "idx_user_symbol_status", columnList = "user_id, symbol, status")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_client_order", columnNames = { "user_id", "client_order_id" })
})

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class Order {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id")
  private Long orderId;

  @Column(name = "client_order_id", nullable = false, length = 64)
  private String clientOrderId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "symbol", nullable = false, length = 32)
  private String symbol;

  @Enumerated(EnumType.STRING)
  @Column(name = "order_side", nullable = false, length = 10)
  private OrderSide orderSide;

  @Enumerated(EnumType.STRING)
  @Column(name = "order_type", nullable = false, length = 10)
  private OrderType orderType;

  @Enumerated(EnumType.STRING)
  @Column(name = "time_in_force", nullable = false, length = 10)
  private TimeInForce timeInForce;

  @Column(name = "price", precision = 18, scale = 8)
  private BigDecimal price;

  @Column(name = "quantity", nullable = false, precision = 18, scale = 8)
  private BigDecimal quantity;

  @Builder.Default
  @Column(name = "filled_quantity", precision = 18, scale = 8)
  private BigDecimal filledQuantity = BigDecimal.ZERO;

  @Column(name = "remaining_quantity", nullable = false, precision = 18, scale = 8)
  private BigDecimal remainingQuantity;

  @Column(name = "avg_filled_price", precision = 18, scale = 8)
  private BigDecimal avgFilledPrice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OrderStatus status;

  @Column(name = "reject_reason", length = 128)
  private String rejectReason;

  @Column(name = "create_at")
  private LocalDateTime createdAt;

  @Column(name = "update_at")
  private LocalDateTime updatedAt;

  @Column(name = "last_event_seq")
  private Long lastEventSeq;

  @Column(name = "last_event_time")
  private LocalDateTime lastEventTime;

  // =====================================
  // 정적 팩토리 메서드 (생성 책임)
  // =====================================

  /**
   * 주문 생성
   */
  public static Order create(
      Long userId,
      String clientOrderId,
      String symbol,
      OrderSide orderSide,
      OrderType orderType,
      TimeInForce timeInForce,
      BigDecimal price,
      BigDecimal quantity) {
    validateCreate(price, quantity, orderType);

    return Order.builder()
        .orderId(null) // Matching Worker가 발행
        .clientOrderId(clientOrderId)
        .userId(userId)
        .symbol(symbol)
        .orderSide(orderSide)
        .orderType(orderType)
        .timeInForce(timeInForce)
        .price(price)
        .quantity(quantity)
        .filledQuantity(BigDecimal.ZERO)
        .remainingQuantity(quantity)
        .avgFilledPrice(null)
        .status(OrderStatus.NEW)
        .rejectReason(null)
        .lastEventSeq(null)
        .lastEventTime(null)
        .build();
  }

  // =====================================
  // 도메인 메서드 (상태 변경 책임)
  // =====================================
  /**
   * orderId 할당 (Matching Worker가 발행한 ID)
   */
  public void assignOrderId(Long orderId) {
    if (this.orderId != null) {
      throw new CustomException(DomainErrorCode.INVALID_STATE);
    }
    this.orderId = orderId;
  }

  /**
   * 주문 체결 (부분 체결 포함)
   */
  public void fill(BigDecimal executedQuantity) {
    if (this.status != OrderStatus.NEW && this.status != OrderStatus.PARTIALLY_FILLED) {
      throw new CustomException(DomainErrorCode.INVALID_STATE);
    }

    this.filledQuantity = this.filledQuantity.add(executedQuantity);

    // 완전 체결 체크
    if (this.filledQuantity.compareTo(this.quantity) >= 0) {
      this.status = OrderStatus.FILLED;
    } else {
      this.status = OrderStatus.PARTIALLY_FILLED;
    }
  }

  /**
   * 주문 취소
   */
  public void cancel() {
    if (this.status != OrderStatus.NEW && this.status != OrderStatus.PARTIALLY_FILLED) {
      throw new CustomException(DomainErrorCode.OPERATION_NOT_ALLOWED);
    }
    this.status = OrderStatus.CANCELED;
  }

  /**
   * 주문 거부
   */
  public void reject() {
    if (this.status != OrderStatus.NEW) {
      throw new CustomException(DomainErrorCode.INVALID_STATE);
    }
    this.status = OrderStatus.REJECTED;
  }

  /**
   * 주문 만료
   */
  public void expire() {
    if (this.status != OrderStatus.NEW) {
      throw new CustomException(DomainErrorCode.INVALID_STATE);
    }
    this.status = OrderStatus.EXPIRED;
  }

  /**
   * 이벤트 처리 후 시퀀스 업데이트
   * - 이벤트 순서 보장
   * - 더 낮은 시퀀스의 이벤트는 무시
   *
   * @param eventSeq  이벤트 시퀀스 번호
   * @param eventTime 이벤트 발생 시각
   * @return 이벤트 처리 여부 (true: 처리됨, false: 무시됨)
   */

  public boolean updateEventInfo(Long eventSeq, LocalDateTime eventTime) {
    // 첫 이벤트이거나, 더 높은 시퀀스인 경우에만 업데이트
    if (this.lastEventSeq == null || eventSeq > this.lastEventSeq) {
      this.lastEventSeq = eventSeq;
      this.lastEventTime = eventTime;
      return true;
    }

    // 중복/순서 위반 이벤트 무시
    return false;
  }

  // =====================================
  // 조회 메서드
  // =====================================

  /**
   * 활성 주문 여부 (체결 가능 상태)
   */
  public boolean isActive() {
    return this.status == OrderStatus.NEW || this.status == OrderStatus.PARTIALLY_FILLED;
  }

  /**
   * 취소 가능 여부
   */
  public boolean isCancelable() {
    return isActive();
  }

  /**
   * 완전 체결 여부
   */
  public boolean isFullyFilled() {
    return this.status == OrderStatus.FILLED;
  }

  /**
   * 부분 체결 여부
   */
  public boolean isPartiallyFilled() {
    return this.status == OrderStatus.PARTIALLY_FILLED;
  }

  // =====================================
  // 비즈니스 검증 로직
  // =====================================

  private static void validateCreate(BigDecimal price, BigDecimal quantity, OrderType orderType) {
    // LIMIT 주문은 가격 필수
    if (orderType == OrderType.LIMIT && (price == null || price.compareTo(BigDecimal.ZERO) <= 0)) {
      throw new CustomException(DomainErrorCode.INVALID_PARAM);
    }

    // 수량 검증
    if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
      throw new CustomException(DomainErrorCode.INVALID_PARAM);
    }
  }
}
