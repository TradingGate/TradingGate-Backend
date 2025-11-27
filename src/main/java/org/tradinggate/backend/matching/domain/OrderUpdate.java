package org.tradinggate.backend.matching.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.tradinggate.backend.matching.domain.e.*;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class OrderUpdate {

    private final long orderId;
    private final long accountId;
    private final String clientOrderId;
    private final String symbol;

    private final OrderSide side;
    private final OrderType orderType;
    private final TimeInForce timeInForce;

    private final long price;
    private final long quantity;
    private final long filledQuantity;
    private final long remainingQuantity;
    private final long avgFilledPrice;

    private final OrderStatus previousStatus;
    private final OrderStatus newStatus;
    private final String reasonCode;

    private final int eventSeq;
    private final String eventType;
    private final long eventTimeMillis;

    public static OrderUpdate of(
            Order order,
            OrderStatus previousStatus,
            String reasonCode,
            String eventType,
            long eventTimeMillis
    ) {
        int seq = order.nextEventSeq();
        return OrderUpdate.builder()
                .orderId(order.getOrderId())
                .accountId(order.getAccountId())
                .clientOrderId(order.getClientOrderId())
                .symbol(order.getSymbol())
                .side(order.getSide())
                .orderType(order.getOrderType())
                .timeInForce(order.getTimeInForce())
                .price(order.getPrice())
                .quantity(order.getQuantity())
                .filledQuantity(order.getFilledQuantity())
                .remainingQuantity(order.getRemainingQuantity())
                .avgFilledPrice(order.getAvgFilledPrice())
                .previousStatus(previousStatus)
                .newStatus(order.getStatus())
                .reasonCode(reasonCode)
                .eventSeq(seq)
                .eventType(eventType)
                .eventTimeMillis(eventTimeMillis)
                .build();
    }
}

