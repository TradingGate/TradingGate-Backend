package org.tradinggate.backend.matching.domain;

import lombok.*;
import org.tradinggate.backend.matching.domain.e.OrderSide;
import org.tradinggate.backend.matching.domain.e.OrderStatus;
import org.tradinggate.backend.matching.domain.e.OrderType;
import org.tradinggate.backend.matching.domain.e.TimeInForce;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class Order {

    private long orderId;
    private long accountId;
    private String clientOrderId;
    private String symbol;

    private OrderSide side;
    private OrderType orderType;
    private TimeInForce timeInForce;

    private long price;
    private long quantity;

    private long remainingQuantity;
    private long filledQuantity;
    private long avgFilledPrice;

    private OrderStatus status;
    private String rejectReason;

    private long receivedAtMillis;
    private long createdAtMillis;
    private long updatedAtMillis;
    private int lastEventSeq;

    public static Order createNew(
            long orderId,
            long accountId,
            String clientOrderId,
            String symbol,
            OrderSide side,
            OrderType orderType,
            TimeInForce timeInForce,
            long price,
            long quantity,
            long receivedAtMillis,
            long createdAtMillis
    ) {
        return Order.builder()
                .orderId(orderId)
                .accountId(accountId)
                .clientOrderId(clientOrderId)
                .symbol(symbol)
                .side(side)
                .orderType(orderType)
                .timeInForce(timeInForce)
                .price(price)
                .quantity(quantity)
                .remainingQuantity(quantity)
                .filledQuantity(0L)
                .avgFilledPrice(0L)
                .status(OrderStatus.NEW)
                .rejectReason(null)
                .receivedAtMillis(receivedAtMillis)
                .createdAtMillis(createdAtMillis)
                .updatedAtMillis(createdAtMillis)
                .lastEventSeq(0)
                .build();
    }

    public void applyFill(long execQuantity, long execPrice, long eventTimeMillis) {
        if (execQuantity <= 0) {
            throw new IllegalArgumentException("execQuantity must be positive");
        }
        if (remainingQuantity < execQuantity) {
            throw new IllegalArgumentException("execQuantity exceeds remaining quantity");
        }

        long newFilledQuantity = this.filledQuantity + execQuantity;
        long newRemainingQuantity = this.remainingQuantity - execQuantity;

        if (this.filledQuantity == 0) {
            this.avgFilledPrice = execPrice;
        } else {
            long totalValueBefore = this.avgFilledPrice * this.filledQuantity;
            long totalValueNew = totalValueBefore + execPrice * execQuantity;
            this.avgFilledPrice = totalValueNew / newFilledQuantity;
        }

        this.filledQuantity = newFilledQuantity;
        this.remainingQuantity = newRemainingQuantity;
        this.updatedAtMillis = eventTimeMillis;

        if (newRemainingQuantity == 0) {
            this.status = OrderStatus.FILLED;
        } else {
            this.status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    public void cancel(String reasonCode, long eventTimeMillis) {
        if (isTerminal()) {
            return;
        }
        this.remainingQuantity = 0L;
        this.status = OrderStatus.CANCELED;
        this.rejectReason = reasonCode;
        this.updatedAtMillis = eventTimeMillis;
    }

    public void reject(String reasonCode, long eventTimeMillis) {
        this.remainingQuantity = 0L;
        this.filledQuantity = 0L;
        this.status = OrderStatus.REJECTED;
        this.rejectReason = reasonCode;
        this.updatedAtMillis = eventTimeMillis;
    }

    public boolean hasRemaining() {
        return remainingQuantity > 0;
    }

    public boolean isTerminal() {
        return status == OrderStatus.FILLED
                || status == OrderStatus.CANCELED
                || status == OrderStatus.REJECTED
                || status == OrderStatus.EXPIRED;
    }

    public int nextEventSeq() {
        this.lastEventSeq += 1;
        return this.lastEventSeq;
    }
}