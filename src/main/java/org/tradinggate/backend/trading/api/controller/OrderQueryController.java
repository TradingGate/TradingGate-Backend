package org.tradinggate.backend.trading.api.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [A-1] Trading API - 주문 조회 Controller
 *
 * 역할:
 * - Trading DB에서 주문 상태/이력 조회
 * - 사용자별/심볼별 필터링
 *
 * TODO:
 * [ ] GET /api/orders - 주문 목록 조회
 *     - Query Params: userId, symbol, status, startDate, endDate
 *     - OrderQueryService.getOrders() 호출
 *     - Pagination 지원 (page, size)
 *
 * [ ] GET /api/orders/{orderId} - 단일 주문 상세 조회
 *     - Path Variable: orderId
 *     - OrderQueryService.getOrderById() 호출
 *
 * [ ] GET /api/orders/client/{clientOrderId} - clientOrderId로 조회
 *     - 멱등성 체크용 조회
 *     - OrderQueryService.getOrderByClientOrderId() 호출
 *
 * 참고: PDF 2-4 (조회 흐름)
 */
@RestController
@RequestMapping("/api/orders")
@Profile("api")
public class OrderQueryController {

  // TODO: OrderQueryService 주입

  // TODO: getOrders() 구현

  // TODO: getOrderById() 구현

  // TODO: getOrderByClientOrderId() 구현
}
