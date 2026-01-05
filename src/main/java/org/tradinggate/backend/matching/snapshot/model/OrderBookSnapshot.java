package org.tradinggate.backend.matching.snapshot.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.tradinggate.backend.matching.snapshot.model.e.ChecksumAlgorithm;
import org.tradinggate.backend.matching.snapshot.model.e.CompressionType;
import org.tradinggate.backend.matching.snapshot.model.e.SnapshotTriggerReason;

import java.util.List;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class OrderBookSnapshot {

    private final String symbol;

    private final long nextOrderId;
    private final long nextMatchId;

    private final long bidLevelCount;
    private final long askLevelCount;
    private final long orderCount;

    private final List<PriceLevelSnapshot> bids;
    private final List<PriceLevelSnapshot> asks;

    @JsonCreator
    public static OrderBookSnapshot create(
            @JsonProperty("symbol") String symbol,
            @JsonProperty("nextOrderId") long nextOrderId,
            @JsonProperty("nextMatchId") long nextMatchId,
            @JsonProperty("bids") List<PriceLevelSnapshot> bids,
            @JsonProperty("asks") List<PriceLevelSnapshot> asks
    ) {
        List<PriceLevelSnapshot> safeBids = (bids == null) ? List.of() : List.copyOf(bids);
        List<PriceLevelSnapshot> safeAsks = (asks == null) ? List.of() : List.copyOf(asks);

        long orderCount = 0L;
        for (PriceLevelSnapshot lvl : safeBids) orderCount += (lvl.getOrders() == null ? 0 : lvl.getOrders().size());
        for (PriceLevelSnapshot lvl : safeAsks) orderCount += (lvl.getOrders() == null ? 0 : lvl.getOrders().size());

        return OrderBookSnapshot.builder()
                .symbol(symbol)
                .nextOrderId(nextOrderId)
                .nextMatchId(nextMatchId)
                .bidLevelCount(safeBids.size())
                .askLevelCount(safeAsks.size())
                .orderCount(orderCount)
                .bids(safeBids)
                .asks(safeAsks)
                .build();
    }
}
