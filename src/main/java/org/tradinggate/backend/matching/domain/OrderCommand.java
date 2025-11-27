package org.tradinggate.backend.matching.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.tradinggate.backend.matching.domain.e.CommandType;
import org.tradinggate.backend.matching.domain.e.OrderSide;
import org.tradinggate.backend.matching.domain.e.OrderType;
import org.tradinggate.backend.matching.domain.e.TimeInForce;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class OrderCommand {

    private final CommandType commandType;
    private final long accountId;
    private final String clientOrderId;
    private final String symbol;
    private final OrderSide side;
    private final OrderType orderType;
    private final TimeInForce timeInForce;
    private final BigDecimal price;
    private final BigDecimal quantity;
    private final CancelTarget cancelTarget;
    private final String source;
    private final Instant receivedAt;

    public static OrderCommand newOrder(
            long accountId,
            String clientOrderId,
            String symbol,
            OrderSide side,
            OrderType orderType,
            TimeInForce timeInForce,
            BigDecimal price,
            BigDecimal quantity,
            String source,
            Instant receivedAt
    ) {
        return OrderCommand.builder()
                .commandType(CommandType.NEW)
                .accountId(accountId)
                .clientOrderId(clientOrderId)
                .symbol(symbol)
                .side(side)
                .orderType(orderType)
                .timeInForce(timeInForce)
                .price(price)
                .quantity(quantity)
                .cancelTarget(null)
                .source(source)
                .receivedAt(receivedAt)
                .build();
    }

    public static OrderCommand cancelOrder(
            long accountId,
            String symbol,
            CancelTarget cancelTarget,
            String source,
            Instant receivedAt
    ) {
        return OrderCommand.builder()
                .commandType(CommandType.CANCEL)
                .accountId(accountId)
                .clientOrderId(null)
                .symbol(symbol)
                .side(null)
                .orderType(null)
                .timeInForce(null)
                .price(null)
                .quantity(null)
                .cancelTarget(cancelTarget)
                .source(source)
                .receivedAt(receivedAt)
                .build();
    }
}
