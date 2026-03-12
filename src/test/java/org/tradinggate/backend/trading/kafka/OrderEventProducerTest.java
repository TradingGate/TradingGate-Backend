package org.tradinggate.backend.trading.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.tradinggate.backend.global.exception.CustomException;
import org.tradinggate.backend.global.exception.TradingErrorCode;
import org.tradinggate.backend.global.kafka.producer.KafkaMessageProducer;
import org.tradinggate.backend.trading.domain.entity.Order;
import org.tradinggate.backend.trading.domain.entity.OrderSide;
import org.tradinggate.backend.trading.domain.entity.OrderType;
import org.tradinggate.backend.trading.domain.entity.TimeInForce;
import org.tradinggate.backend.trading.kafka.producer.OrderEventProducer;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventProducer 테스트")
class OrderEventProducerTest {

  @Mock
  private KafkaMessageProducer kafkaMessageProducer;

  @Spy
  private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @InjectMocks
  private OrderEventProducer orderEventProducer;

  @Test
  @DisplayName("신규 주문 발행 성공 시 fast send 경로를 사용한다")
  void publishNewOrder_UsesFastSend() {
    ReflectionTestUtils.setField(orderEventProducer, "apiSendTimeoutMs", 3000L);

    Order order = Order.create(
        1L,
        "cli-20260310-0001",
        "BTCUSDT",
        OrderSide.BUY,
        OrderType.LIMIT,
        TimeInForce.GTC,
        new BigDecimal("50000"),
        BigDecimal.ONE);

    orderEventProducer.publishNewOrder(order);

    verify(kafkaMessageProducer).sendAndWaitOnce(anyString(), anyString(), anyString(), anyLong());
  }

  @Test
  @DisplayName("브로커 장애 시 서비스 불가 예외로 변환한다")
  void publishNewOrder_WhenKafkaUnavailable_ThrowsServiceUnavailable() {
    ReflectionTestUtils.setField(orderEventProducer, "apiSendTimeoutMs", 3000L);

    Order order = Order.create(
        1L,
        "cli-20260310-0002",
        "BTCUSDT",
        OrderSide.BUY,
        OrderType.LIMIT,
        TimeInForce.GTC,
        new BigDecimal("50000"),
        BigDecimal.ONE);

    doThrow(new RuntimeException("Kafka send failed fast"))
        .when(kafkaMessageProducer)
        .sendAndWaitOnce(anyString(), anyString(), anyString(), anyLong());

    CustomException ex = assertThrows(CustomException.class, () -> orderEventProducer.publishNewOrder(order));
    assertEquals(TradingErrorCode.MESSAGE_BROKER_UNAVAILABLE.getStatus(), ex.getStatusCode());
    assertEquals(TradingErrorCode.MESSAGE_BROKER_UNAVAILABLE.getMessage(), ex.getMessage());
  }
}
