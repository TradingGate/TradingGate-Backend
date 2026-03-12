package org.tradinggate.backend.trading.api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.tradinggate.backend.global.common.CommonResponse;
import org.tradinggate.backend.trading.api.dto.request.OrderQueryRequest;
import org.tradinggate.backend.trading.api.dto.response.OrderResponse;
import org.tradinggate.backend.trading.service.OrderQueryService;

/**
 * [A-1] Trading API - 주문 조회 Controller
 *
 * 역할:
 * - Trading DB에서 주문 상태/이력 조회
 * - 사용자별/심볼별 필터링
 */
@RestController
@RequestMapping("/api/orders")
@Profile("api")
@RequiredArgsConstructor
public class OrderQueryController {

  private final OrderQueryService orderQueryService;

  @GetMapping
  public ResponseEntity<CommonResponse<Page<OrderResponse>>> getOrders(
      @RequestHeader("userId") Long userId,
      @ModelAttribute OrderQueryRequest request,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

    Page<OrderResponse> orders = orderQueryService.getOrders(userId, request, pageable);
    return ResponseEntity.ok(CommonResponse.success(orders));
  }

  @GetMapping("/{orderId}")
  public ResponseEntity<CommonResponse<OrderResponse>> getOrderById(
      @RequestHeader("userId") Long userId,
      @PathVariable Long orderId) {

    OrderResponse order = orderQueryService.getOrderById(userId, orderId);
    return ResponseEntity.ok(CommonResponse.success(order));
  }

  @GetMapping("/client/{clientOrderId}")
  public ResponseEntity<CommonResponse<OrderResponse>> getOrderByClientOrderId(
      @RequestHeader("userId") Long userId,
      @PathVariable String clientOrderId) {

    OrderResponse order = orderQueryService.getOrderByClientOrderId(userId, clientOrderId);
    return ResponseEntity.ok(CommonResponse.success(order));
  }
}
