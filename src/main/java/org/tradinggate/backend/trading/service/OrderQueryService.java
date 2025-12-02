package org.tradinggate.backend.trading.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * [A-1] Trading API - 주문 조회 서비스
 *
 * 역할:
 * - Trading DB에서 주문 정보 읽기
 * - Read-Only 작업
 *
 * TODO:
 * [ ] getOrders(OrderQueryRequest, Pageable) - 조건별 주문 목록 조회
 *     - OrderRepository.findByUserIdAndSymbol() 등 활용
 *     - Pagination 처리
 *     - OrderResponse 리스트 반환
 *
 * [ ] getOrderById(Long orderId) - 단일 주문 조회
 *     - OrderRepository.findByOrderId()
 *     - 없으면 OrderNotFoundException
 *
 * [ ] getOrderByClientOrderId(Long userId, String clientOrderId)
 *     - 멱등성 체크용 조회
 *     - OrderRepository.findByUserIdAndClientOrderId()
 *
 * 참고: PDF 2-4 (Projection Consumer → Trading DB)
 */
@Service
@Profile("api")
public class OrderQueryService {

  // TODO: OrderRepository 주입

  // TODO: getOrders() 구현

  // TODO: getOrderById() 구현

  // TODO: getOrderByClientOrderId() 구현
}
