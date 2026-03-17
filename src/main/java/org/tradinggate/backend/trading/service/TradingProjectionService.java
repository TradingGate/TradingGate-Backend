package org.tradinggate.backend.trading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.time.OffsetDateTime;
import java.util.Optional;

@Slf4j
@Service
@Profile("projection")
@RequiredArgsConstructor
public class TradingProjectionService {

  private final OrderRepository orderRepository;
  private final TradeRepository tradeRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public void applyOrderUpdate(String message) throws Exception {
    JsonNode body = extractBody(message);

    Long orderId = longValue(body, "orderId");
    Long userId = longValue(body, "accountId");
    String clientOrderId = text(body, "clientOrderId");
    Long eventSeq = body.hasNonNull("eventSeq") ? body.get("eventSeq").asLong() : null;
    LocalDateTime eventTime = dateTime(body, "eventTime");

    Optional<Order> existingOrder = orderRepository.findByUserIdAndClientOrderId(userId, clientOrderId)
        .or(() -> orderRepository.findByUserIdAndOrderId(userId, orderId));

    if (existingOrder.isEmpty()) {
      orderRepository.save(Order.createProjection(
          orderId,
          userId,
          clientOrderId,
          text(body, "symbol"),
          OrderSide.valueOf(text(body, "side")),
          OrderType.valueOf(text(body, "orderType")),
          TimeInForce.valueOf(text(body, "timeInForce")),
          decimalOrNull(body, "price"),
          decimal(body, "quantity"),
          decimal(body, "filledQuantity"),
          decimal(body, "remainingQuantity"),
          decimalOrNull(body, "avgFilledPrice"),
          OrderStatus.valueOf(text(body, "newStatus")),
          textOrNull(body, "reasonCode"),
          eventSeq,
          eventTime
      ));
      return;
    }

    Order order = existingOrder.get();

    if (eventSeq != null && !order.updateEventInfo(eventSeq, eventTime)) {
      return;
    }

    order.applyProjectionUpdate(
        orderId,
        text(body, "symbol"),
        OrderSide.valueOf(text(body, "side")),
        OrderType.valueOf(text(body, "orderType")),
        TimeInForce.valueOf(text(body, "timeInForce")),
        decimalOrNull(body, "price"),
        decimal(body, "quantity"),
        decimal(body, "filledQuantity"),
        decimal(body, "remainingQuantity"),
        decimalOrNull(body, "avgFilledPrice"),
        OrderStatus.valueOf(text(body, "newStatus")),
        textOrNull(body, "reasonCode"),
        eventTime
    );
  }

  @Transactional
  public void applyTradeExecuted(String message) throws Exception {
    JsonNode body = extractBody(message);

    String eventId = text(body, "eventId");
    Long tradeId = longValue(body, "tradeId");
    Long userId = longValue(body, "userId");

    if (tradeRepository.findByEventId(eventId).isPresent()
        || tradeRepository.findByTradeIdAndUserId(tradeId, userId).isPresent()) {
      return;
    }

    String symbol = text(body, "symbol");
    String liquidityFlag = text(body, "liquidityFlag");
    Long orderId = "TAKER".equalsIgnoreCase(liquidityFlag)
        ? longValue(body, "takerOrderId")
        : longValue(body, "makerOrderId");

    Trade trade = Trade.create(
        eventId,
        tradeId,
        longValue(body, "matchId"),
        orderId,
        userId,
        symbol,
        OrderSide.valueOf(text(body, "side")),
        decimal(body, "execQuantity"),
        decimal(body, "execPrice"),
        BigDecimal.ZERO,
        deriveQuoteAsset(symbol),
        liquidityFlag,
        dateTime(body, "execTime")
    );

    try {
      tradeRepository.save(trade);
    } catch (DataIntegrityViolationException e) {
      // Duplicate replay can arrive with a different eventId but the same logical trade.
      if (tradeRepository.findByTradeIdAndUserId(tradeId, userId).isPresent()) {
        log.info("Skip duplicate trades.executed projection. eventId={}, tradeId={}, userId={}",
            eventId, tradeId, userId);
        return;
      }
      throw e;
    }
  }

  private JsonNode extractBody(String message) throws Exception {
    JsonNode root = objectMapper.readTree(message);
    return root.hasNonNull("body") ? root.get("body") : root;
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      throw new IllegalArgumentException("Missing required field: " + field);
    }
    return value.asText();
  }

  private String textOrNull(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  private Long longValue(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      throw new IllegalArgumentException("Missing required field: " + field);
    }
    return value.asLong();
  }

  private BigDecimal decimal(JsonNode node, String field) {
    return new BigDecimal(text(node, field));
  }

  private BigDecimal decimalOrNull(JsonNode node, String field) {
    String raw = textOrNull(node, field);
    return raw == null || raw.isBlank() ? null : new BigDecimal(raw);
  }

  private LocalDateTime dateTime(JsonNode node, String field) {
    String raw = text(node, field);
    return OffsetDateTime.parse(raw).toLocalDateTime();
  }

  private String deriveQuoteAsset(String symbol) {
    if (symbol.endsWith("USDT")) {
      return "USDT";
    }
    if (symbol.endsWith("KRW")) {
      return "KRW";
    }
    if (symbol.endsWith("BTC")) {
      return "BTC";
    }
    if (symbol.endsWith("ETH")) {
      return "ETH";
    }
    return symbol;
  }
}
