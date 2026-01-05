package org.tradinggate.backend.matching.engine.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayDeque;
import java.util.Deque;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceLevel {

    private long price;
    private final Deque<Order> orders = new ArrayDeque<>();
    private long totalQuantity;

    public PriceLevel(long price) {
        this.price = price;
    }

    /**
     * 새 주문을 이 가격 레벨에 추가 (FIFO)
     */
    public void addOrder(Order order) {
        if (order == null || !order.hasRemaining()) {
            return;
        }
        orders.addLast(order);
        totalQuantity += order.getRemainingQuantity();
    }

    /**
     * 해당 주문을 큐에서 제거 (주로 취소 시)
     */
    public void removeOrder(Order target, long previousRemaining) {
        if (target == null) {
            return;
        }

        boolean removed = orders.removeIf(o -> o.getOrderId() == target.getOrderId());

        if (removed) {
            totalQuantity = Math.max(0, totalQuantity - previousRemaining);
            if (orders.isEmpty() || totalQuantity == 0L) {
                recalcTotalQuantity();
            }
        }
    }

    /**
     * 주문의 잔량이 변경되었을 때(totalQuantity 보정)
     */
    public void onOrderQuantityChanged(long previousRemaining, long newRemaining) {
        long diff = previousRemaining - newRemaining;
        if (diff <= 0) {
            return;
        }
        totalQuantity = Math.max(0, totalQuantity - diff);
    }

    /**
     * 현재 레벨에서 매칭 대상으로 쓸 수 있는 "최상단 주문" 반환
     * (잔량 0인 주문은 앞에서 정리)
     */
    public Order bestOrder() {
        while (!orders.isEmpty() && !orders.peekFirst().hasRemaining()) {
            orders.pollFirst();
        }
        if (orders.isEmpty()) {
            recalcTotalQuantity();
            return null;
        }
        return orders.peekFirst();
    }

    public boolean isEmpty() {
        return bestOrder() == null;
    }

    private void recalcTotalQuantity() {
        long sum = 0L;
        for (Order o : orders) {
            if (o.hasRemaining()) {
                sum += o.getRemainingQuantity();
            }
        }
        this.totalQuantity = sum;
    }

    public static PriceLevel restore(long price) {
        PriceLevel lvl = new PriceLevel();
        lvl.price = price;
        lvl.totalQuantity = 0L;
        return lvl;
    }

    public void addRestoredOrder(Order order) {
        this.orders.addLast(order);
    }

    public void restoreTotalQuantity(long totalQuantity) {
        this.totalQuantity = totalQuantity;
    }
}