package org.tradinggate.backend.trading.api.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [A-1] Trading API - 주문 접수 Controller
 *
 * 역할:
 * - HTTP POST 요청으로 신규 주문/취소 요청 받기
 * - 유저 인증/권한 검증 후 Service로 위임
 * - HTTP 202 Accepted 응답 반환
 *
 * TODO:
 * [ ] POST /api/orders - 신규 주문 엔드포인트 구현
 *     - Request Body: OrderCreateRequest 받기
 *     - 인증 정보에서 userId 추출
 *     - OrderService.createOrder() 호출
 *     - HTTP 202 + clientOrderId 반환
 *
 * [ ] POST /api/orders/cancel - 주문 취소 엔드포인트 구현
 *     - Request Body: OrderCancelRequest 받기
 *     - OrderService.cancelOrder() 호출
 *     - HTTP 202 응답
 *
 * [ ] 예외 처리 추가
 *     - DuplicateOrderException -> 409 Conflict
 *     - RiskBlockedException -> 403 Forbidden
 *     - InvalidOrderException -> 400 Bad Request
 *     - OrderNotFoundException -> 404 Not Found
 *
 * [ ] @Profile("api") 어노테이션 확인
 *
 * 참고: PDF 2-1, 2-2 (HTTP 요청 플로우)
 */
@RestController
@RequestMapping("/api/orders")
@Profile("api")
public class OrderController {

  // TODO: OrderService 주입

  // TODO: createOrder() 구현

  // TODO: cancelOrder() 구현
}
