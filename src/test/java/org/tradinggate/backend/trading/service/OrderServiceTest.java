package org.tradinggate.backend.trading.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradinggate.backend.trading.api.dto.request.OrderCancelRequest;
import org.tradinggate.backend.trading.api.dto.request.OrderCreateRequest;
import org.tradinggate.backend.trading.domain.entity.OrderSide;
import org.tradinggate.backend.trading.domain.entity.OrderType;
import org.tradinggate.backend.trading.domain.entity.TimeInForce;
import org.tradinggate.backend.trading.domain.repository.OrderRepository;
import org.tradinggate.backend.trading.exception.DuplicateOrderException;
import org.tradinggate.backend.trading.kafka.producer.OrderEventProducer;
import org.tradinggate.backend.trading.api.validator.OrderValidator;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * OrderService 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("주문 서비스 테스트")
class OrderServiceTest {

    @Mock // ✅ @MockBean → @Mock으로 변경
    private IdempotencyService idempotencyService;

    @Mock // ✅ @MockBean → @Mock으로 변경
    private OrderEventProducer orderEventProducer;

    @Mock
    private RiskCheckService riskCheckService;

    @Mock
    private OrderValidator orderValidator;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("신규 주문 생성 - 성공")
    void createOrder_Success() {
        // given
        Long userId = 1L;
        OrderCreateRequest request = OrderCreateRequest.builder()
                .clientOrderId("cli-20241204-0001")
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderType(OrderType.LIMIT)
                .timeInForce(TimeInForce.GTC)
                .price(new BigDecimal("50000.00"))
                .quantity(new BigDecimal("0.1"))
                .build();

        when(riskCheckService.isBlocked(anyLong(), anyString())).thenReturn(false);
        doNothing().when(idempotencyService).checkAndLock(anyLong(), anyString());

        // Order.
        doNothing().when(orderEventProducer)
                .publishNewOrder(any(org.tradinggate.backend.trading.domain.entity.Order.class));

        // when
        OrderService.OrderCreateResponse response = orderService.createOrder(request, userId);

        // then
        assertNotNull(response);
        assertEquals("cli-20241204-0001", response.getClientOrderId());
        assertTrue(response.getReceived());

        verify(riskCheckService).isBlocked(userId, "BTCUSDT");
        verify(orderValidator).validate(request);
        verify(idempotencyService).checkAndLock(userId, "cli-20241204-0001");
        verify(orderEventProducer).publishNewOrder(any(org.tradinggate.backend.trading.domain.entity.Order.class));
    }

    @Test
    @DisplayName("중복 주문 - 멱등성 체크 실패")
    void createOrder_DuplicateOrder_ThrowsException() {
        // given
        Long userId = 1L;
        OrderCreateRequest request = OrderCreateRequest.builder()
                .clientOrderId("cli-20241204-0001")
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderType(OrderType.LIMIT)
                .timeInForce(TimeInForce.GTC)
                .price(new BigDecimal("50000.00"))
                .quantity(new BigDecimal("0.1"))
                .build();

        when(riskCheckService.isBlocked(anyLong(), anyString())).thenReturn(false);
        doThrow(new DuplicateOrderException("Duplicate order"))
                .when(idempotencyService).checkAndLock(anyLong(), anyString());

        // when & then
        assertThrows(DuplicateOrderException.class,
                () -> orderService.createOrder(request, userId));

        verify(orderEventProducer, never())
                .publishNewOrder(any(org.tradinggate.backend.trading.domain.entity.Order.class));
    }

    @Test
    @DisplayName("주문 생성 중 예외 발생 - 멱등성 키 삭제")
    void createOrder_KafkaFails_ReleasesIdempotencyLock() {
        // given
        Long userId = 1L;
        OrderCreateRequest request = OrderCreateRequest.builder()
                .clientOrderId("cli-20241204-0001")
                .symbol("BTCUSDT")
                .side(OrderSide.BUY)
                .orderType(OrderType.LIMIT)
                .timeInForce(TimeInForce.GTC)
                .price(new BigDecimal("50000.00"))
                .quantity(new BigDecimal("0.1"))
                .build();

        when(riskCheckService.isBlocked(anyLong(), anyString())).thenReturn(false);
        doNothing().when(idempotencyService).checkAndLock(anyLong(), anyString());
        // Since we pass an order object, match any Order
        doThrow(new RuntimeException("Kafka error"))
                .when(orderEventProducer)
                .publishNewOrder(any(org.tradinggate.backend.trading.domain.entity.Order.class));

        // when & then
        assertThrows(RuntimeException.class,
                () -> orderService.createOrder(request, userId));

        verify(idempotencyService).markFailed(userId, "cli-20241204-0001");
    }

    @Test
    @DisplayName("주문 취소 - 성공")
    void cancelOrder_Success() {
        // given
        Long userId = 1L;
        OrderCancelRequest request = OrderCancelRequest.builder()
                .clientOrderId("cli-20241204-0001")
                .symbol("BTCUSDT")
                .build();

        org.tradinggate.backend.trading.domain.entity.Order mockOrder = org.tradinggate.backend.trading.domain.entity.Order
                .create(
                        userId,
                        "cli-20241204-0001",
                        "BTCUSDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        TimeInForce.GTC,
                        BigDecimal.TEN,
                        BigDecimal.ONE);

        when(orderRepository.findByUserIdAndClientOrderId(anyLong(), anyString()))
                .thenReturn(java.util.Optional.of(mockOrder));
        doNothing().when(orderEventProducer)
                .publishCancelOrder(any(org.tradinggate.backend.trading.domain.entity.Order.class));

        // when
        OrderService.OrderCancelResponse response = orderService.cancelOrder(request, userId);

        // then
        assertNotNull(response);
        assertEquals("cli-20241204-0001", response.getClientOrderId());
        assertTrue(response.getReceived());

        verify(orderEventProducer).publishCancelOrder(mockOrder);
    }
}
