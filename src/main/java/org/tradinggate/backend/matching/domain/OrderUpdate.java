package org.tradinggate.backend.matching.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.tradinggate.backend.matching.domain.e.*;

import java.time.Instant;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class OrderUpdate {

    private final String eventId;
    private final long orderId;
    private final String clientOrderId;
    private final long accountId;
    private final Instant receivedAt;

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

    private final String eventType;
    private final int eventSeq;
    private final Instant eventTime;
    private final long eventTimeMillis;

    public static OrderUpdate of(
            String eventId,
            Order order,
            OrderStatus previousStatus,
            String reasonCode,
            String eventType,
            long eventTimeMillis
    ) {
        int seq = order.nextEventSeq();
        Instant eventTime = Instant.ofEpochMilli(eventTimeMillis);
        return OrderUpdate.builder()
                .eventId(eventId)
                .orderId(order.getOrderId())
                .accountId(order.getAccountId())
                .receivedAt(Instant.ofEpochMilli(order.getReceivedAtMillis()))
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
                .eventTime(eventTime)
                .eventTimeMillis(eventTimeMillis)
                .build();
    }
}
