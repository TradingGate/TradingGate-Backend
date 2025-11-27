package org.tradinggate.backend.matching.domain;

import lombok.Getter;
import org.tradinggate.backend.matching.domain.e.OrderSide;

import java.util.*;

import static java.util.Comparator.reverseOrder;

@Getter
public class OrderBook {

    private final String symbol;

    private final NavigableMap<Long, PriceLevel> bidPriceLevels = new TreeMap<>(reverseOrder());
    private final NavigableMap<Long, PriceLevel> askPriceLevels = new TreeMap<>();

    private final Map<Long, Order> orderIndex = new HashMap<>();
    private final Map<String, Long> clientOrderIndex = new HashMap<>();

    private long nextOrderId = 1L;
    private long nextMatchId = 1L;

    private OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public static OrderBook create(String symbol) {
        return new OrderBook(symbol);
    }

    public long nextOrderId() {
        return nextOrderId++;
    }

    public long nextMatchId() {
        return nextMatchId++;
    }

    public void indexNewOrder(Order order) {
        orderIndex.put(order.getOrderId(), order);

        if (order.getClientOrderId() != null) {
            String key = clientKey(order.getAccountId(), order.getClientOrderId());
            clientOrderIndex.put(key, order.getOrderId());
        }
    }

    public Optional<Order> findByOrderId(long orderId) {
        return Optional.ofNullable(orderIndex.get(orderId));
    }

    public Optional<Order> findByClientOrder(long accountId, String clientOrderId) {
        if (clientOrderId == null || clientOrderId.isBlank()) return Optional.empty();

        String key = clientKey(accountId, clientOrderId);
        Long orderId = clientOrderIndex.get(key);
        if (orderId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(orderIndex.get(orderId));
    }

    public void addToBook(Order order) {
        if (!order.hasRemaining()) return;
        NavigableMap<Long, PriceLevel> book =  order.getSide() == OrderSide.BUY ? bidPriceLevels : askPriceLevels;

        PriceLevel level = book.computeIfAbsent(order.getPrice(), PriceLevel::new);
        level.addOrder(order);
    }

    public void removeFromBook(Order order) {
        NavigableMap<Long, PriceLevel> book = order.getSide() == OrderSide.BUY ? bidPriceLevels : askPriceLevels;

        PriceLevel level = book.get(order.getPrice());
        if (level == null) return;

        long previousRemaining = order.getRemainingQuantity();
        level.removeOrder(order, previousRemaining);

        if (level.isEmpty()) book.remove(order.getPrice());
    }

    /**
     * 주문 잔량 변경에 따른 totalQuantity 보정
     */
    public void onOrderFilled(Order order, long previousRemaining) {
        if (order == null) return;
        if (previousRemaining == order.getRemainingQuantity()) return;

        NavigableMap<Long, PriceLevel> book = order.getSide() == OrderSide.BUY ? bidPriceLevels : askPriceLevels;
        PriceLevel level = book.get(order.getPrice());

        if (level == null) return;

        level.onOrderQuantityChanged(previousRemaining, order.getRemainingQuantity());
        if (level.isEmpty()) book.remove(order.getPrice());
    }

    private String clientKey(long accountId, String clientOrderId) {
        return accountId + ":" + clientOrderId;
    }

    public Optional<Order> bestBid() {
        while (true) {
            Map.Entry<Long, PriceLevel> entry = bidPriceLevels.firstEntry();
            if (entry == null) {
                return Optional.empty();
            }
            PriceLevel level = entry.getValue();
            Order best = level.bestOrder();

            if (best == null) {
                bidPriceLevels.remove(entry.getKey());
                continue;
            }
            return Optional.of(best);
        }
    }

    public Optional<Order> bestAsk() {
        while (true) {
            Map.Entry<Long, PriceLevel> entry = askPriceLevels.firstEntry();
            if (entry == null) {
                return Optional.empty();
            }
            PriceLevel level = entry.getValue();
            Order best = level.bestOrder();

            if (best == null) {
                askPriceLevels.remove(entry.getKey());
                continue;
            }
            return Optional.of(best);
        }
    }

}
