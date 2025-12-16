package org.tradinggate.backend.trading.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.global.exception.CustomException;
import org.tradinggate.backend.global.exception.UserErrorCode;
import org.tradinggate.backend.trading.api.dto.request.OrderQueryRequest;
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
 * 역할:
 * - Trading DB에서 주문 정보 읽기
 * - Read-Only 작업
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Log4j2
public class OrderQueryService {

  private final OrderRepository orderRepository;

  /**
   * 조건별 주문 목록 조회
   */
  public Page<OrderResponse> getOrders(Long userId, OrderQueryRequest request, Pageable pageable) {
    Page<Order> orders = orderRepository.findByConditions(
        userId,
        request.getSymbol(),
        request.getStatus(),
        request.getStartDate(),
        request.getEndDate(),
        pageable);
    return orders.map(OrderResponse::from);
  }

  /**
   * 주문 ID로 단건 조회
   */
  public OrderResponse getOrderById(Long userId, Long orderId) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new OrderNotFoundException("주문을 찾을 수 없습니다: " + orderId));

    validateUserAccess(order, userId);
    return OrderResponse.from(order);
  }

  /**
   * ClientOrderId로 주문 조회 (멱등성 체크용)
   */
  public OrderResponse getOrderByClientOrderId(Long userId, String clientOrderId) {
    Order order = orderRepository.findByUserIdAndClientOrderId(userId, clientOrderId)
        .orElseThrow(() -> new OrderNotFoundException("ClientOrderId: " + clientOrderId));

    // ClientOrderId 조회도 본인 확인 필요
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
      LocalDateTime endDate) {
    List<Order> orders = orderRepository.findByUserIdAndCreatedAtBetween(
        userId, startDate, endDate);
    return orders.stream()
        .map(OrderResponse::from)
        .collect(Collectors.toList());
  }

  /**
   * 미체결 주문 조회
   */
  public List<OrderResponse> getPendingOrders(Long userId) {
    return getOrdersByStatus(userId, OrderStatus.NEW);
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