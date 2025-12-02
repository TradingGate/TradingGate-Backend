package org.tradinggate.backend.trading.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * [A-1] Trading API - 주문 비즈니스 로직
 *
 * 역할:
 * - 주문 생성/취소의 핵심 비즈니스 로직
 * - 검증 → 멱등성 체크 → Kafka 발행
 * - ⚠️ DB에는 쓰지 않음 (Kafka만 발행) ⚠️
 *
 * TODO:
 * [ ] createOrder(OrderCreateRequest, Long userId) 구현:
 *     1. 유저 권한 검증 (해당 심볼 거래 가능한지)
 *     2. OrderValidator.validate() 호출 (틱/스텝 검증)
 *     3. RiskCheckService.isBlocked(userId, symbol) 호출
 *        → 차단 상태면 RiskBlockedException 발생
 *     4. IdempotencyService.checkAndLock(userId, clientOrderId)
 *        → 중복이면 DuplicateOrderException 발생
 *     5. OrderEventProducer.publishNewOrder() - Kafka 발행
 *     6. 임시 응답 반환 (clientOrderId, received=true)
 *
 * [ ] cancelOrder(OrderCancelRequest, Long userId) 구현:
 *     1. 주문 소유권 검증 (DB 조회 or 토큰)
 *        → OrderRepository.findByUserIdAndClientOrderId()
 *        → 소유자 불일치 시 예외
 *     2. 이미 체결/취소된 주문인지 확인
 *        → status가 FILLED/CANCELED면 에러 응답
 *     3. OrderEventProducer.publishCancelOrder() - Kafka 발행
 *     4. 응답 반환
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
@Service
@Profile("api")
public class OrderService {

  // TODO: OrderValidator, RiskCheckService, IdempotencyService 주입
  // TODO: OrderEventProducer 주입
  // TODO: OrderRepository 주입 (취소 시 소유권 검증용)

  // TODO: createOrder() 구현

  // TODO: cancelOrder() 구현
}
