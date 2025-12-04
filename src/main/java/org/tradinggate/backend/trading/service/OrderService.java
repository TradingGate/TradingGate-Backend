package org.tradinggate.backend.trading.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.tradinggate.backend.trading.api.dto.request.OrderCancelRequest;
import org.tradinggate.backend.trading.api.dto.request.OrderCreateRequest;
import org.tradinggate.backend.trading.api.validator.OrderValidator;
import org.tradinggate.backend.trading.domain.entity.OrderStatus;
import org.tradinggate.backend.trading.domain.repository.OrderRepository;
import org.tradinggate.backend.trading.exception.OrderNotFoundException;
import org.tradinggate.backend.trading.kafka.producer.OrderEventProducer;

/**
 * [A-1] Trading API - 주문 비즈니스 로직
 *
 * 역할:
 * - 주문 생성/취소의 핵심 비즈니스 로직
 * - 검증 → 멱등성 체크 → Kafka 발행
 * - ⚠️ DB에는 쓰지 않음 (Kafka만 발행) ⚠️
 *
 * TODO:
 * [🔼] createOrder(OrderCreateRequest, Long userId) 구현:
 *     1. 유저 권한 검증 (해당 심볼 거래 가능한지)✅️
 *     2. OrderValidator.validate() 호출 (틱/스텝 검증)
 *     3. RiskCheckService.isBlocked(userId, symbol) 호출
 *        → 차단 상태면 RiskBlockedException 발생
 *     4. IdempotencyService.checkAndLock(userId, clientOrderId)✅️
 *        → 중복이면 DuplicateOrderException 발생
 *     5. OrderEventProducer.publishNewOrder() - Kafka 발행✅️
 *     6. 임시 응답 반환 (clientOrderId, received=true)✅️
 *
 * [🔼] cancelOrder(OrderCancelRequest, Long userId) 구현:
 *     1. 주문 소유권 검증 (DB 조회 or 토큰)
 *        → OrderRepository.findByUserIdAndClientOrderId()
 *        → 소유자 불일치 시 예외
 *     2. 이미 체결/취소된 주문인지 확인
 *        → status가 FILLED/CANCELED면 에러 응답
 *     3. OrderEventProducer.publishCancelOrder() - Kafka 발행✅️
 *     4. 응답 반환✅️
 *
 * [ ] 트랜잭션 처리 없음 (DB 쓰기 X, Kafka만 발행)
 *
 * [ ] 예외 처리:
 *     - DuplicateOrderException
 *     - RiskBlockedException
 *     - InvalidOrderException
 *     - OrderNotFoundException
 *
 * 참고: PDF 2-2 (A-1 Trading API 내부 처리)
 */
@Slf4j
@Service
@Profile("api")
@RequiredArgsConstructor
public class OrderService {

  private final IdempotencyService idempotencyService;
  private final OrderEventProducer orderEventProducer;

  /**신규 주문 생성*/
  public OrderCreateResponse createOrder(OrderCreateRequest request, Long userId) {
    log.info("Creating order: userId={}, clientOrderId={}", userId, request.getClientOrderId());

    // 1. 멱등성 체크
    idempotencyService.checkAndLock(userId, request.getClientOrderId());

    try {
      // 2. Kafka orders.in 발행
      orderEventProducer.publishNewOrder(request, userId);

      // 3. 응답 반환
      return OrderCreateResponse.builder()
          .clientOrderId(request.getClientOrderId())
          .received(true)
          .message("Order received")
          .build();

    } catch (Exception e) {
      idempotencyService.markFailed(userId, request.getClientOrderId());
      throw e;
    }
  }

  /**주문 취소*/
  public OrderCancelResponse cancelOrder(OrderCancelRequest request, Long userId) {
    log.info("Cancelling order: userId={}, clientOrderId={}", userId, request.getClientOrderId());

    // Kafka orders.in 취소 이벤트 발행
    orderEventProducer.publishCancelOrder(request, userId);

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