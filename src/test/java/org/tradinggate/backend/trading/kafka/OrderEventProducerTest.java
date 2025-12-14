package org.tradinggate.backend.trading.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.tradinggate.backend.trading.api.dto.request.OrderCreateRequest;
import org.tradinggate.backend.trading.domain.entity.Order;
import org.tradinggate.backend.trading.domain.entity.OrderSide;
import org.tradinggate.backend.trading.domain.entity.OrderType;
import org.tradinggate.backend.trading.domain.entity.TimeInForce;
import org.tradinggate.backend.trading.kafka.event.OrderCancelEvent;
import org.tradinggate.backend.trading.kafka.event.OrderEvent;
import org.tradinggate.backend.trading.kafka.producer.OrderEventProducer;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.support.SendResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderEventProducer 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Kafka Producer 테스트")
@SuppressWarnings("null")
class OrderEventProducerTest {

  @Mock
  private KafkaTemplate<String, Object> kafkaTemplate;

  @Spy
  private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @InjectMocks
  private OrderEventProducer orderEventProducer;

  @Test
  @DisplayName("신규 주문 이벤트 발행 - key=symbol")
  void publishNewOrder_Success() throws Exception {
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

    // Stub KafkaTemplate
    RecordMetadata metadata = mock(RecordMetadata.class);
    when(metadata.partition()).thenReturn(1);
    SendResult<String, Object> sendResult = mock(SendResult.class);
    when(sendResult.getRecordMetadata()).thenReturn(metadata);

    when(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(sendResult));

    // when
    Order order = Order.create(
        userId,
        request.getClientOrderId(),
        request.getSymbol(),
        request.getOrderSide(),
        request.getOrderType(),
        request.getTimeInForce(),
        request.getPrice(),
        request.getQuantity());
    orderEventProducer.publishNewOrder(order);

    // then
    ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);

    verify(objectMapper).writeValueAsString(eventCaptor.capture());
    verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), anyString());

    assertEquals("orders.in", topicCaptor.getValue());
    assertEquals("BTCUSDT", keyCaptor.getValue()); // key = symbol

    OrderEvent event = eventCaptor.getValue();
    assertEquals("NEW", event.getCommandType());
    assertEquals(userId, event.getUserId());
    assertEquals("cli-20241204-0001", event.getClientOrderId());
    assertEquals("BTCUSDT", event.getSymbol());
    assertEquals(OrderSide.BUY, event.getSide());
    assertEquals("API", event.getSource());
  }

  @Test
  @DisplayName("주문 취소 이벤트 발행")
  void publishCancelOrder_Success() throws Exception {
    // given
    Long userId = 1L;

    Order order = Order.create(
        userId,
        "cli-20241204-0001",
        "BTCUSDT",
        OrderSide.BUY,
        OrderType.LIMIT,
        TimeInForce.GTC,
        BigDecimal.TEN,
        BigDecimal.ONE);

    // Stub KafkaTemplate
    RecordMetadata metadata = mock(RecordMetadata.class);
    when(metadata.partition()).thenReturn(1);
    SendResult<String, Object> sendResult = mock(SendResult.class);
    when(sendResult.getRecordMetadata()).thenReturn(metadata);

    when(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(sendResult));

    // when
    orderEventProducer.publishCancelOrder(order);

    // then
    ArgumentCaptor<OrderCancelEvent> eventCaptor = ArgumentCaptor.forClass(OrderCancelEvent.class);

    verify(objectMapper).writeValueAsString(eventCaptor.capture());
    verify(kafkaTemplate).send(eq("orders.in"), eq("BTCUSDT"), anyString());

    OrderCancelEvent event = eventCaptor.getValue();
    assertEquals("CANCEL", event.getCommandType());
    assertEquals(userId, event.getUserId());
    assertEquals("cli-20241204-0001", event.getClientOrderId());
    assertNotNull(event.getCancelTarget());
    assertEquals("CLIENT_ORDER_ID", event.getCancelTarget().getBy());
  }
}
