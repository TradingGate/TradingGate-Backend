package org.tradinggate.backend.matching.engine.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.tradinggate.backend.matching.engine.model.CancelTarget;
import org.tradinggate.backend.matching.engine.model.MatchFill;
import org.tradinggate.backend.matching.engine.model.OrderCommand;
import org.tradinggate.backend.matching.engine.model.e.CommandType;
import org.tradinggate.backend.matching.engine.model.e.OrderSide;
import org.tradinggate.backend.matching.engine.model.e.OrderType;
import org.tradinggate.backend.matching.engine.model.e.TimeInForce;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class KafkaJsonUtil {

    public static OrderCommand parseOrderCommand(ObjectMapper objectMapper, String payload) throws Exception {
        JsonNode root = objectMapper.readTree(payload);

        String commandTypeStr = getRequiredText(root, "commandType");
        CommandType commandType = CommandType.valueOf(commandTypeStr);

        long accountId = getRequiredLong(root, "userId");
        String symbol = getRequiredText(root, "symbol");
        String source = getOptionalText(root, "source", "UNKNOWN");
        Instant receivedAt = Instant.parse(getOptionalText(root, "receivedAt", Instant.now().toString()));

        if (commandType == CommandType.NEW) {
            String clientOrderId = getRequiredText(root, "clientOrderId");
            OrderSide side = OrderSide.valueOf(getRequiredText(root, "side"));
            OrderType orderType = OrderType.valueOf(getRequiredText(root, "orderType"));
            TimeInForce tif = TimeInForce.valueOf(getRequiredText(root, "timeInForce"));

            BigDecimal price = null;
            if (orderType == OrderType.LIMIT) {
                price = new BigDecimal(getRequiredText(root, "price"));
            }
            BigDecimal quantity = new BigDecimal(getRequiredText(root, "quantity"));

            return OrderCommand.newOrder(
                    accountId,
                    clientOrderId,
                    symbol,
                    side,
                    orderType,
                    tif,
                    price,
                    quantity,
                    source,
                    receivedAt
            );
        } else { // CANCEL
            JsonNode cancelNode = root.get("cancelTarget");
            if (cancelNode == null || cancelNode.isNull()) {
                throw new IllegalArgumentException("CANCEL command must have cancelTarget");
            }

            String by = getRequiredText(cancelNode, "by");
            String value = getRequiredText(cancelNode, "value");

            CancelTarget cancelTarget;
            switch (by) {
                case "CLIENT_ORDER_ID" -> cancelTarget = CancelTarget.byClientOrderId(value);
                case "ORDER_ID" -> cancelTarget = CancelTarget.byOrderId(Long.parseLong(value));
                default -> throw new IllegalArgumentException("Unknown cancelTarget.by = " + by);
            }

            return OrderCommand.cancelOrder(
                    accountId,
                    symbol,
                    cancelTarget,
                    source,
                    receivedAt
            );
        }
    }

    private static String getRequiredText(JsonNode node, String fieldName) {
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull() || valueNode.asText().isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
        return valueNode.asText();
    }

    private static String getOptionalText(JsonNode node, String fieldName, String defaultValue) {
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            return defaultValue;
        }
        String text = valueNode.asText();
        return (text == null || text.isBlank()) ? defaultValue : text;
    }

    private static long getRequiredLong(JsonNode node, String fieldName) {
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            throw new IllegalArgumentException("Missing required long field: " + fieldName);
        }
        return valueNode.asLong();
    }

    public static Map<String, Object> buildTakerPayload(MatchFill fill,
                                                         long tradeId,
                                                         long matchId,
                                                         Instant execTime,
                                                         String execQtyStr,
                                                         String execPriceStr,
                                                         String eventId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("tradeId", tradeId);
        payload.put("matchId", matchId);
        payload.put("symbol", fill.getSymbol());
        payload.put("takerOrderId", fill.getTakerOrderId());
        payload.put("makerOrderId", fill.getMakerOrderId());
        payload.put("userId", fill.getTakerAccountId());
        payload.put("side", fill.getTakerSide().name());
        payload.put("execQuantity", execQtyStr);
        payload.put("execPrice", execPriceStr);
        payload.put("liquidityFlag", "TAKER");
        payload.put("execTime", execTime);
        return payload;
    }

    public static Map<String, Object> buildMakerPayload(MatchFill fill,
                                                        long tradeId,
                                                        long matchId,
                                                        Instant execTime,
                                                        String execQtyStr,
                                                        String execPriceStr,
                                                        String eventId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("tradeId", tradeId);
        payload.put("matchId", matchId);
        payload.put("symbol", fill.getSymbol());
        payload.put("takerOrderId", fill.getTakerOrderId());
        payload.put("makerOrderId", fill.getMakerOrderId());
        payload.put("userId", fill.getMakerAccountId());
        payload.put("side", fill.getMakerSide().name());
        payload.put("execQuantity", execQtyStr);
        payload.put("execPrice", execPriceStr);
        payload.put("liquidityFlag", "MAKER");
        payload.put("execTime", execTime);
        return payload;
    }
}
