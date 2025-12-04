package org.tradinggate.backend.trading.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.tradinggate.backend.trading.api.dto.request.OrderCancelRequest;
import org.tradinggate.backend.trading.api.dto.request.OrderCreateRequest;
import org.tradinggate.backend.trading.domain.entity.OrderSide;
import org.tradinggate.backend.trading.domain.entity.OrderType;
import org.tradinggate.backend.trading.domain.entity.TimeInForce;
import org.tradinggate.backend.trading.kafka.event.OrderCancelEvent;
import org.tradinggate.backend.trading.kafka.event.OrderEvent;
import org.tradinggate.backend.trading.kafka.producer.OrderEventProducer;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderEventProducer 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Kafka Producer 테스트")
class OrderEventProducerTest {

  @Mock
  private KafkaTemplate<String, Object> kafkaTemplate;

  @InjectMocks
  private OrderEventProducer orderEventProducer;

  @Test
  @DisplayName("신규 주문 이벤트 발행 - key=symbol")
  void publishNewOrder_Success() {
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

    // when
    orderEventProducer.publishNewOrder(request, userId);

    // then
    ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);

    verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());

    assertEquals("orders.in", topicCaptor.getValue());
    assertEquals("BTCUSDT", keyCaptor.getValue());  // key = symbol

    OrderEvent event = eventCaptor.getValue();
    assertEquals("NEW", event.getCommandType());
    assertEquals(userId, event.getUserId());
    assertEquals("cli-20241204-0001", event.getClientOrderId());
    assertEquals("BTCUSDT", event.getSymbol());
    assertEquals("BUY", event.getSide());
    assertEquals("API", event.getSource());
  }

  @Test
  @DisplayName("주문 취소 이벤트 발행")
  void publishCancelOrder_Success() {
    // given
    Long userId = 1L;
    OrderCancelRequest request = OrderCancelRequest.builder()
        .clientOrderId("cli-20241204-0001")
        .symbol("BTCUSDT")
        .build();

    // when
    orderEventProducer.publishCancelOrder(request, userId);

    // then
    ArgumentCaptor<OrderCancelEvent> eventCaptor = ArgumentCaptor.forClass(OrderCancelEvent.class);
    verify(kafkaTemplate).send(eq("orders.in"), eq("BTCUSDT"), eventCaptor.capture());

    OrderCancelEvent event = eventCaptor.getValue();
    assertEquals("CANCEL", event.getCommandType());
    assertEquals(userId, event.getUserId());
    assertEquals("cli-20241204-0001", event.getClientOrderId());
    assertNotNull(event.getCancelTarget());
    assertEquals("CLIENT_ORDER_ID", event.getCancelTarget().getBy());
  }
}
