package org.tradinggate.backend.matching.snapshot.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.tradinggate.backend.matching.engine.model.Order;
import org.tradinggate.backend.matching.engine.model.e.OrderSide;
import org.tradinggate.backend.matching.engine.model.e.OrderStatus;
import org.tradinggate.backend.matching.engine.model.e.OrderType;
import org.tradinggate.backend.matching.engine.model.e.TimeInForce;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class OrderSnapshot {

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

    private final OrderStatus status;
    private final String rejectReason;

    private final long receivedAtMillis;
    private final long createdAtMillis;
    private final long updatedAtMillis;

    private final int lastEventSeq;

    public static OrderSnapshot fromDomain(Order o) {
        return OrderSnapshot.builder()
                .orderId(o.getOrderId())
                .accountId(o.getAccountId())
                .clientOrderId(o.getClientOrderId())
                .symbol(o.getSymbol())
                .side(o.getSide())
                .orderType(o.getOrderType())
                .timeInForce(o.getTimeInForce())
                .price(o.getPrice())
                .quantity(o.getQuantity())
                .filledQuantity(o.getFilledQuantity())
                .remainingQuantity(o.getRemainingQuantity())
                .avgFilledPrice(o.getAvgFilledPrice())
                .status(o.getStatus())
                .rejectReason(o.getRejectReason())
                .receivedAtMillis(o.getReceivedAtMillis())
                .createdAtMillis(o.getCreatedAtMillis())
                .updatedAtMillis(o.getUpdatedAtMillis())
                .lastEventSeq(o.getLastEventSeq())
                .build();
    }
}