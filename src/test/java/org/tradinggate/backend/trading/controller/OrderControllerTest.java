package org.tradinggate.backend.trading.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;  // ✅ 추가
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.tradinggate.backend.trading.api.controller.OrderController;
import org.tradinggate.backend.trading.api.dto.request.OrderCancelRequest;
import org.tradinggate.backend.trading.api.dto.request.OrderCreateRequest;
import org.tradinggate.backend.trading.domain.entity.OrderSide;
import org.tradinggate.backend.trading.domain.entity.OrderType;
import org.tradinggate.backend.trading.domain.entity.TimeInForce;
import org.tradinggate.backend.trading.service.OrderService;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

/**
 * OrderController 테스트
 */
@WebMvcTest(
    controllers = OrderController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
    }
)
@ActiveProfiles("api")  // ✅ 추가
@DisplayName("주문 Controller 테스트")
@SuppressWarnings("removal")
class OrderControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private OrderService orderService;

  @Test
  @DisplayName("POST /api/orders - 신규 주문 성공")
  void createOrder_Success() throws Exception {
    // given
    OrderCreateRequest request = OrderCreateRequest.builder()
        .clientOrderId("cli-20241204-0001")
        .symbol("BTCUSDT")
        .side(OrderSide.BUY)
        .orderType(OrderType.LIMIT)
        .timeInForce(TimeInForce.GTC)
        .price(new BigDecimal("50000.00"))
        .quantity(new BigDecimal("0.1"))
        .build();

    OrderService.OrderCreateResponse response = OrderService.OrderCreateResponse.builder()
        .clientOrderId("cli-20241204-0001")
        .received(true)
        .message("Order received")
        .build();

    when(orderService.createOrder(any(OrderCreateRequest.class), anyLong()))
        .thenReturn(response);

    // when
    MvcResult result = mockMvc.perform(post("/api/orders")
            .header("X-User-Id", "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andReturn();

    // then
    assertEquals(202, result.getResponse().getStatus());
  }

  @Test
  @DisplayName("POST /api/orders - Validation 실패")
  void createOrder_ValidationFail() throws Exception {
    // given
    String invalidJson = """
                {
                    "symbol": "BTCUSDT",
                    "side": "BUY",
                    "orderType": "LIMIT",
                    "timeInForce": "GTC",
                    "price": 50000.00,
                    "quantity": 0.1
                }
                """;

    // when
    MvcResult result = mockMvc.perform(post("/api/orders")
            .header("X-User-Id", "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidJson))
        .andDo(print())
        .andReturn();

    // then
    assertEquals(400, result.getResponse().getStatus());
  }

  @Test
  @DisplayName("POST /api/orders/cancel - 주문 취소 성공")
  void cancelOrder_Success() throws Exception {
    // given
    OrderCancelRequest request = OrderCancelRequest.builder()
        .clientOrderId("cli-20241204-0001")
        .symbol("BTCUSDT")
        .build();

    OrderService.OrderCancelResponse response = OrderService.OrderCancelResponse.builder()
        .clientOrderId("cli-20241204-0001")
        .received(true)
        .message("Cancel request received")
        .build();

    when(orderService.cancelOrder(any(OrderCancelRequest.class), anyLong()))
        .thenReturn(response);

    // when
    MvcResult result = mockMvc.perform(post("/api/orders/cancel")
            .header("X-User-Id", "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andReturn();

    // then
    assertEquals(202, result.getResponse().getStatus());
  }
}
