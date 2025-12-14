package org.tradinggate.backend.trading.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tradinggate.backend.trading.api.dto.request.OrderCancelRequest;
import org.tradinggate.backend.trading.api.dto.request.OrderCreateRequest;
import org.tradinggate.backend.trading.service.OrderService;

/**
 * [A-1] Trading API - 주문 접수 Controller
 * 역할:
 * - HTTP POST 요청으로 신규 주문/취소 요청 받기
 * - 유저 인증/권한 검증 후 Service로 위임
 * - HTTP 202 Accepted 응답 반환
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@Profile("api")
@RequiredArgsConstructor
@Validated
public class OrderController {

        private final OrderService orderService;

        /** 신규 주문 생성 */
        @PostMapping
        public ResponseEntity<OrderService.OrderCreateResponse> createOrder(
                        @Valid @RequestBody OrderCreateRequest request,
                        @AuthenticationPrincipal Long userId) {
                log.info("Received order creation request: userId={}, clientOrderId={}",
                                userId, request.getClientOrderId());

                OrderService.OrderCreateResponse response = orderService.createOrder(request, userId);

                return ResponseEntity
                                .status(HttpStatus.ACCEPTED) // 202
                                .body(response);
        }

        /** 주문 취소 */
        @PostMapping("/cancel")
        public ResponseEntity<OrderService.OrderCancelResponse> cancelOrder(
                        @Valid @RequestBody OrderCancelRequest request,
                        @AuthenticationPrincipal Long userId) {
                log.info("Received order cancel request: userId={}, clientOrderId={}",
                                userId, request.getClientOrderId());

                OrderService.OrderCancelResponse response = orderService.cancelOrder(request, userId);

                return ResponseEntity
                                .status(HttpStatus.ACCEPTED) // 202
                                .body(response);
        }
}