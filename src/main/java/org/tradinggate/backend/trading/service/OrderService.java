package org.tradinggate.backend.trading.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.tradinggate.backend.global.exception.CustomException;
import org.tradinggate.backend.global.exception.TradingErrorCode;
import org.tradinggate.backend.trading.api.dto.request.OrderCancelRequest;
import org.tradinggate.backend.trading.api.dto.request.OrderCreateRequest;
import org.tradinggate.backend.trading.api.validator.OrderValidator;
import org.tradinggate.backend.trading.domain.entity.Order;
import org.tradinggate.backend.trading.domain.entity.OrderStatus;
import org.tradinggate.backend.trading.domain.repository.OrderRepository;
import org.tradinggate.backend.trading.kafka.producer.OrderEventProducer;

@Slf4j
@Service
@Profile("api")
@RequiredArgsConstructor
public class OrderService {

  private final IdempotencyService idempotencyService;
  private final OrderEventProducer orderEventProducer;
  private final OrderValidator orderValidator;
  private final RiskCheckService riskCheckService;
  private final OrderRepository orderRepository;

  /** 신규 주문 생성 */
  public OrderCreateResponse createOrder(OrderCreateRequest request, Long userId) {
    log.info("Creating order: userId={}, clientOrderId={}", userId, request.getClientOrderId());

    // 1. 유저 권한 및 리스크 상태 검증
    riskCheckService.isBlocked(userId, request.getSymbol());

    // 2. 주문 유효성 검증 (틱/스텝, 필수값)
    orderValidator.validate(request);

    // 3. 멱등성 체크
    idempotencyService.checkAndLock(userId, request.getClientOrderId());

    try {
      // 4. Kafka orders.in 발행 (Order 객체 생성만 하고 저장하지 않음)
      Order order = Order.create(
          userId,
          request.getClientOrderId(),
          request.getSymbol(),
          request.getOrderSide(),
          request.getOrderType(),
          request.getTimeInForce(),
          request.getPrice(),
          request.getQuantity());

      orderEventProducer.publishNewOrder(order);

      // 5. 응답 반환
      return OrderCreateResponse.builder()
          .clientOrderId(request.getClientOrderId())
          .received(true)
          .message("Order received")
          .build();

    } catch (Exception e) {
      log.error("Failed to process create order", e);
      idempotencyService.markFailed(userId, request.getClientOrderId());
      throw e;
    }
  }

  /** 주문 취소 */
  public OrderCancelResponse cancelOrder(OrderCancelRequest request, Long userId) {
    log.info("Cancelling order: userId={}, clientOrderId={}", userId, request.getClientOrderId());

    // 1. 주문 소유권 검증 (DB 조회)
    // clientOrderId 또는 orderId로 조회가 필요하나, request에는 clientOrderId가 필수
    Order order = orderRepository.findByUserIdAndClientOrderId(userId, request.getClientOrderId())
        .orElseThrow(
            () -> new CustomException(TradingErrorCode.ORDER_NOT_FOUND));

    // 2. 이미 종료된 주문인지 확인
    if (order.getStatus() == OrderStatus.FILLED ||
        order.getStatus() == OrderStatus.CANCELED ||
        order.getStatus() == OrderStatus.REJECTED ||
        order.getStatus() == OrderStatus.EXPIRED) {
      throw new CustomException(TradingErrorCode.INVALID_ORDER);
    }

    // 3. Kafka orders.in 취소 이벤트 발행
    orderEventProducer.publishCancelOrder(order);

    return OrderCancelResponse.builder()
        .clientOrderId(request.getClientOrderId())
        .received(true)
        .message("Cancel request received")
        .build();
  }

  @lombok.Data
  @lombok.Builder
  public static class OrderCreateResponse {
    private String clientOrderId;
    private Boolean received;
    private String message;
  }

  @lombok.Data
  @lombok.Builder
  public static class OrderCancelResponse {
    private String clientOrderId;
    private Boolean received;
    private String message;
  }
}