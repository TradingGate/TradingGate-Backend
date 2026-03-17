package org.tradinggate.backend.trading.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradinggate.backend.trading.domain.entity.Order;
import org.tradinggate.backend.trading.domain.entity.OrderSide;
import org.tradinggate.backend.trading.domain.entity.OrderStatus;
import org.tradinggate.backend.trading.domain.entity.OrderType;
import org.tradinggate.backend.trading.domain.entity.TimeInForce;
import org.tradinggate.backend.trading.domain.entity.Trade;
import org.tradinggate.backend.trading.domain.repository.OrderRepository;
import org.tradinggate.backend.trading.domain.repository.TradeRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradingProjectionServiceTest {

  @Mock
  private OrderRepository orderRepository;

  @Mock
  private TradeRepository tradeRepository;

  private TradingProjectionService tradingProjectionService;

  @BeforeEach
  void setUp() {
    tradingProjectionService = new TradingProjectionService(orderRepository, tradeRepository, new ObjectMapper());
  }

  @Test
  void applyOrderUpdate_usesUserAndClientOrderIdBeforeOrderId() throws Exception {
    Order existing = Order.createProjection(
        999L,
        505L,
        "client-505",
        "005930",
        OrderSide.BUY,
        OrderType.LIMIT,
        TimeInForce.GTC,
        new BigDecimal("70000"),
        new BigDecimal("1"),
        BigDecimal.ZERO,
        new BigDecimal("1"),
        null,
        OrderStatus.NEW,
        null,
        1L,
        LocalDateTime.parse("2026-03-11T12:00:00")
    );

    when(orderRepository.findByUserIdAndClientOrderId(505L, "client-505"))
        .thenReturn(Optional.of(existing));

    tradingProjectionService.applyOrderUpdate("""
        {
          "body": {
            "orderId": 1,
            "accountId": 505,
            "clientOrderId": "client-505",
            "symbol": "005930",
            "side": "BUY",
            "orderType": "LIMIT",
            "timeInForce": "GTC",
            "price": "70000",
            "quantity": "1",
            "filledQuantity": "1",
            "remainingQuantity": "0",
            "avgFilledPrice": "70000",
            "newStatus": "FILLED",
            "reasonCode": null,
            "eventSeq": 2,
            "eventTime": "2026-03-11T12:00:01+09:00"
          }
        }
        """);

    verify(orderRepository, never()).findByOrderId(1L);
    verify(orderRepository, never()).findByUserIdAndOrderId(505L, 1L);
    verify(orderRepository, never()).save(any(Order.class));
    assertThat(existing.getOrderId()).isEqualTo(1L);
    assertThat(existing.getStatus()).isEqualTo(OrderStatus.FILLED);
    assertThat(existing.getFilledQuantity()).isEqualByComparingTo("1");
    assertThat(existing.getRemainingQuantity()).isEqualByComparingTo("0");
    assertThat(existing.getAvgFilledPrice()).isEqualByComparingTo("70000");
    assertThat(existing.getLastEventSeq()).isEqualTo(2L);
  }

  @Test
  void applyOrderUpdate_doesNotReuseOrderIdFromDifferentUser() throws Exception {
    when(orderRepository.findByUserIdAndClientOrderId(910000090L, "burst-buy-910000090-90"))
        .thenReturn(Optional.empty());
    when(orderRepository.findByUserIdAndOrderId(910000090L, 1465L))
        .thenReturn(Optional.empty());

    tradingProjectionService.applyOrderUpdate("""
        {
          "body": {
            "orderId": 1465,
            "accountId": 910000090,
            "clientOrderId": "burst-buy-910000090-90",
            "symbol": "BTCUSDT",
            "side": "BUY",
            "orderType": "LIMIT",
            "timeInForce": "GTC",
            "price": "50000",
            "quantity": "1",
            "filledQuantity": "0",
            "remainingQuantity": "1",
            "avgFilledPrice": "0",
            "newStatus": "NEW",
            "reasonCode": null,
            "eventSeq": 1,
            "eventTime": "2026-03-17T12:37:32.187+09:00"
          }
        }
        """);

    verify(orderRepository).findByUserIdAndClientOrderId(910000090L, "burst-buy-910000090-90");
    verify(orderRepository).findByUserIdAndOrderId(910000090L, 1465L);
    verify(orderRepository).save(any(Order.class));
  }

  @Test
  void applyTradeExecuted_skipsWhenEventIdAlreadyExists() throws Exception {
    when(tradeRepository.findByEventId("evt-1"))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(Trade.class)));

    tradingProjectionService.applyTradeExecuted("""
        {
          "body": {
            "eventId": "evt-1",
            "tradeId": 10,
            "matchId": 20,
            "userId": 505,
            "symbol": "005930",
            "side": "BUY",
            "execQuantity": "1",
            "execPrice": "70000",
            "liquidityFlag": "TAKER",
            "makerOrderId": 1,
            "takerOrderId": 2,
            "execTime": "2026-03-11T12:00:01+09:00"
          }
        }
        """);

    verify(tradeRepository, never()).save(any(Trade.class));
  }

  @Test
  void applyTradeExecuted_skipsWhenTradeIdAndUserIdAlreadyExist() throws Exception {
    when(tradeRepository.findByEventId("evt-2"))
        .thenReturn(Optional.empty());
    when(tradeRepository.findByTradeIdAndUserId(10L, 505L))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(Trade.class)));

    tradingProjectionService.applyTradeExecuted("""
        {
          "body": {
            "eventId": "evt-2",
            "tradeId": 10,
            "matchId": 20,
            "userId": 505,
            "symbol": "005930",
            "side": "BUY",
            "execQuantity": "1",
            "execPrice": "70000",
            "liquidityFlag": "TAKER",
            "makerOrderId": 1,
            "takerOrderId": 2,
            "execTime": "2026-03-11T12:00:01+09:00"
          }
        }
        """);

    verify(tradeRepository).findByEventId("evt-2");
    verify(tradeRepository).findByTradeIdAndUserId(10L, 505L);
    verify(tradeRepository, never()).save(any(Trade.class));
  }
}
