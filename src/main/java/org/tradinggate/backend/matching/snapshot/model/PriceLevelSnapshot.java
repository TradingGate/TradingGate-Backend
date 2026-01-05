package org.tradinggate.backend.matching.snapshot.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.tradinggate.backend.matching.engine.model.Order;
import org.tradinggate.backend.matching.engine.model.PriceLevel;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class PriceLevelSnapshot {

    private final long price;
    private final long totalQuantity;
    private final List<OrderSnapshot> orders;

    public static PriceLevelSnapshot fromDomain(PriceLevel level) {
        List<OrderSnapshot> orderSnapshots = new ArrayList<>();
        for (Order o : level.getOrders()) {
            orderSnapshots.add(OrderSnapshot.fromDomain(o));
        }

        return PriceLevelSnapshot.builder()
                .price(level.getPrice())
                .totalQuantity(level.getTotalQuantity())
                .orders(orderSnapshots)
                .build();
    }
}