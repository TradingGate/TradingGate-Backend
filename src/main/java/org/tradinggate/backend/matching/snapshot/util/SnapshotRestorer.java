package org.tradinggate.backend.matching.snapshot.util;

import org.tradinggate.backend.matching.engine.model.Order;
import org.tradinggate.backend.matching.engine.model.OrderBook;
import org.tradinggate.backend.matching.engine.model.PriceLevel;
import org.tradinggate.backend.matching.snapshot.model.OrderBookSnapshot;
import org.tradinggate.backend.matching.snapshot.model.OrderSnapshot;
import org.tradinggate.backend.matching.snapshot.model.PartitionSnapshot;
import org.tradinggate.backend.matching.snapshot.model.PriceLevelSnapshot;

import java.util.ArrayList;
import java.util.List;

public class SnapshotRestorer {

    public List<OrderBook> restorePartition(PartitionSnapshot snapshot) {
        List<OrderBook> result = new ArrayList<>();
        for (OrderBookSnapshot obs : snapshot.getOrderBooks()) {
            result.add(restoreOrderBook(obs));
        }
        return result;
    }

    private OrderBook restoreOrderBook(OrderBookSnapshot snapshot) {
        OrderBook book = OrderBook.create(snapshot.getSymbol());
        restoreSideLevels(book, snapshot.getBids(), true);
        restoreSideLevels(book, snapshot.getAsks(), false);
        book.restoreNextIds(snapshot.getNextOrderId(), snapshot.getNextMatchId());
        return book;
    }


    private void restoreSideLevels(OrderBook book, List<PriceLevelSnapshot> levels, boolean isBid) {
        if (levels == null || levels.isEmpty()) return;

        for (PriceLevelSnapshot lvl : levels) {
            PriceLevel level = PriceLevel.restore(lvl.getPrice());

            for (OrderSnapshot os : lvl.getOrders()) {
                Order order = Order.restore(os);

                level.addRestoredOrder(order);

                book.indexRestoredOrder(order);
            }

            // totalQuantity는 "도메인 값"을 신뢰하고 그대로 세팅 (검증은 TODO)
            level.restoreTotalQuantity(lvl.getTotalQuantity());

            if (isBid) {
                book.restoreBidLevel(level);
            } else {
                book.restoreAskLevel(level);
            }
        }
    }
}
