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
import org.tradinggate.backend.trading.kafka.producer.OrderEventProducer;
import org.tradinggate.backend.trading.api.validator.OrderValidator;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString; // anyString import 추가
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("주문 서비스 단위 테스트")
class OrderServiceTest {

  @Mock
  private OrderEventProducer orderEventProducer;

  @Mock
  private OrderRiskValidationService riskCheckService;

  @Mock
  private OrderValidator orderValidator;

  @Mock // 변경됨: @MockitoBean -> @Mock
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
        .orderSide(OrderSide.BUY)
        .orderType(OrderType.LIMIT)
        .timeInForce(TimeInForce.GTC)
        .price(new BigDecimal("50000.00"))
        .quantity(new BigDecimal("0.1"))
        .build();

    when(riskCheckService.isBlocked(anyLong(), anyString())).thenReturn(false);

    doNothing().when(orderEventProducer)
        .publishNewOrder(any(org.tradinggate.backend.trading.domain.entity.Order.class));

    OrderService.OrderCreateResponse response = orderService.createOrder(request, userId);

    assertNotNull(response);
    assertEquals("cli-20241204-0001", response.getClientOrderId());

    verify(riskCheckService).isBlocked(userId, "BTCUSDT");
    verify(orderValidator).validate(request);
    verify(orderEventProducer)
        .publishNewOrder(any(org.tradinggate.backend.trading.domain.entity.Order.class));
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

    org.tradinggate.backend.trading.domain.entity.Order mockOrder =
        org.tradinggate.backend.trading.domain.entity.Order.create(
            userId,
            "cli-20241204-0001",
            "BTCUSDT",
            OrderSide.BUY,
            OrderType.LIMIT,
            TimeInForce.GTC,
            BigDecimal.TEN,
            BigDecimal.ONE);

    when(orderRepository.findByUserIdAndClientOrderId(anyLong(), anyString()))
        .thenReturn(Optional.of(mockOrder)); // Optional.of 사용

    doNothing().when(orderEventProducer)
        .publishCancelOrder(any(org.tradinggate.backend.trading.domain.entity.Order.class));

    OrderService.OrderCancelResponse response = orderService.cancelOrder(request, userId);

    assertNotNull(response);
    assertEquals("cli-20241204-0001", response.getClientOrderId());

    verify(orderEventProducer).publishCancelOrder(mockOrder);
  }

  @Test
  @DisplayName("신규 주문 생성 - 성공 (로그 확인용)")
  void createOrder_Success_WithLog() {
    System.out.println("========== [1] 테스트 준비 (Given) ==========");
    // given
    Long userId = 1L;
    OrderCreateRequest request = OrderCreateRequest.builder()
        .clientOrderId("cli-20241204-0001")
        .symbol("BTCUSDT")
        .orderSide(OrderSide.BUY)
        .orderType(OrderType.LIMIT)
        .timeInForce(TimeInForce.GTC)
        .price(new BigDecimal("50000.00"))
        .quantity(new BigDecimal("0.1"))
        .build();

    // Mock 행동 정의
    when(riskCheckService.isBlocked(anyLong(), anyString())).thenReturn(false);
    System.out.println("-> 가짜 RiskCheckService: 'isBlocked 호출되면 false 리턴해' 설정 완료");

    System.out.println("========== [2] 실제 코드 실행 (When) ==========");
    // when
    OrderService.OrderCreateResponse response = orderService.createOrder(request, userId);
    System.out.println("-> OrderService 실행 완료. 응답 객체 받음: " + response);

    System.out.println("========== [3] 결과 검증 (Then) ==========");
    // then
    assertNotNull(response);
    System.out.println("-> 검증 1: 응답이 null이 아님 확인 (통과)");

    assertEquals("cli-20241204-0001", response.getClientOrderId());
    System.out.println("-> 검증 2: 주문 ID가 요청한 것과 동일함 확인 (통과)");

    verify(riskCheckService).isBlocked(userId, "BTCUSDT");
    System.out.println("-> 검증 3: RiskCheckService가 실제로 호출되었는지 확인 (통과)");

    verify(orderValidator).validate(request);
    System.out.println("-> 검증 4: Validator가 호출되었는지 확인 (통과)");

    System.out.println("========== 테스트 성공! ==========");
  }
}