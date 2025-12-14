package org.tradinggate.backend.trading.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.global.exception.CustomException;
import org.tradinggate.backend.global.exception.DomainErrorCode;
import org.tradinggate.backend.global.exception.UserErrorCode;
import org.tradinggate.backend.trading.api.dto.response.OrderResponse;
import org.tradinggate.backend.trading.domain.entity.Order;
import org.tradinggate.backend.trading.domain.entity.OrderStatus;
import org.tradinggate.backend.trading.domain.repository.OrderRepository;
import org.tradinggate.backend.trading.exception.OrderNotFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
/**
 * 주문 조회 전용 서비스 (CQRS Query)
 * - @Transactional(readOnly = true) 로 읽기 최적화
 * - 복잡한 조회 쿼리 처리
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Log4j2
public class OrderQueryService {

  private final OrderRepository orderRepository;

  /**
   * 주문 ID로 단건 조회
   */
  public OrderResponse getOrder(Long userId, Long orderId) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new OrderNotFoundException("주문을 찾을 수 없습니다: " + orderId));

    validateUserAccess(order, userId);
    return OrderResponse.from(order);
  }

  /**
   * 사용자의 전체 주문 조회 (Slice 페이징)
   */
  public Slice<OrderResponse> getMyOrders(Long userId, Pageable pageable) {
    Slice<Order> orders = orderRepository.findByUserId(userId, pageable);
    return orders.map(OrderResponse::from);
  }

  /**
   * 사용자의 전체 주문 조회 (Page 페이징)
   */
  public Page<OrderResponse> getMyOrdersWithTotal(Long userId, Pageable pageable) {
    Page<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    return orders.map(OrderResponse::from);
  }

  /**
   * 특정 상태의 주문만 조회
   */
  public List<OrderResponse> getOrdersByStatus(Long userId, OrderStatus status) {
    List<Order> orders = orderRepository.findByUserIdAndStatus(userId, status);
    return orders.stream()
        .map(OrderResponse::from)
        .collect(Collectors.toList());
  }

  /**
   * 특정 심볼의 주문 조회
   */
  public List<OrderResponse> getOrdersBySymbol(Long userId, String symbol) {
    List<Order> orders = orderRepository.findByUserIdAndSymbol(userId, symbol);
    return orders.stream()
        .map(OrderResponse::from)
        .collect(Collectors.toList());
  }

  /**
   * 기간별 주문 조회
   */
  public List<OrderResponse> getOrdersByDateRange(
      Long userId,
      LocalDateTime startDate,
      LocalDateTime endDate
  ) {
    List<Order> orders = orderRepository.findByUserIdAndCreatedAtBetween(
        userId, startDate, endDate
    );
    return orders.stream()
        .map(OrderResponse::from)
        .collect(Collectors.toList());
  }

  /**
   * 미체결 주문 조회
   */
  public List<OrderResponse> getPendingOrders(Long userId) {
    return getOrdersByStatus(userId, OrderStatus.PENDING);
  }

  /**
   * 권한 검증
   */
  private void validateUserAccess(Order order, Long userId) {
    if (!order.getUserId().equals(userId)) {
      throw new CustomException(UserErrorCode.UNAUTHORIZED);
    }
  }
}