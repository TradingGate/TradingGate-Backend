package org.tradinggate.backend.matching.engine.model;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
public class MatchResult {

    private final List<OrderUpdate> orderUpdates = new ArrayList<>();
    private final List<MatchFill> matchFills = new ArrayList<>();

    public static MatchResult empty() {
        return new MatchResult();
    }

    public void addOrderUpdate(OrderUpdate update) {
        this.orderUpdates.add(update);
    }

    public void addMatchFill(MatchFill fill) {
        this.matchFills.add(fill);
    }

    public boolean isEmpty() {
        return orderUpdates.isEmpty() && matchFills.isEmpty();
    }
}
