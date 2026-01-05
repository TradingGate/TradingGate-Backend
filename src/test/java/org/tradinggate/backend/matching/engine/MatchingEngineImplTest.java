package org.tradinggate.backend.matching.engine;

import org.junit.jupiter.api.Test;
import org.tradinggate.backend.matching.engine.kafka.PartitionCountProvider;
import org.tradinggate.backend.matching.engine.kafka.TopicPartitionCountProvider;
import org.tradinggate.backend.matching.engine.model.*;
import org.tradinggate.backend.matching.engine.model.e.OrderSide;
import org.tradinggate.backend.matching.engine.model.e.OrderType;
import org.tradinggate.backend.matching.engine.model.e.TimeInForce;
import org.tradinggate.backend.matching.engine.service.MatchingEngineImpl;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineImplTest {

    /**
     * 1) 반대편 주문이 없는 상태에서 NEW → 오더북에 쌓이고 체결은 발생하지 않는다.
     */
    @Test
    void newBuyOrder_withoutOppositeOrders_shouldBeAddedToOrderBook() {
        // given
        PartitionCountProvider provider = topic -> 12;
        OrderBookRegistry orderBookRegistry = new OrderBookRegistry(provider);
        MatchingEngineImpl engine = new MatchingEngineImpl(orderBookRegistry);
        long now = System.currentTimeMillis();

        OrderCommand buyCmd = OrderCommand.newOrder(
                1001L,
                "cli-1",
                "BTCUSDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                TimeInForce.GTC,
                BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(1),
                "TEST",
                java.time.Instant.ofEpochMilli(now)
        );

        // when
        MatchResult result = engine.handle(buyCmd, now);

        // then
        assertTrue(result.getMatchFills().isEmpty(), "체결은 없어야 함");
        assertEquals(1, result.getOrderUpdates().size(), "CREATED 이벤트 1건 정도 기대");

        OrderBook book = orderBookRegistry.find("BTCUSDT").get();
        assertNotNull(book);

        Optional<Order> bestBidOpt = book.bestBid();
        assertTrue(bestBidOpt.isPresent());

        Order bestBid = bestBidOpt.get();
        assertEquals(OrderSide.BUY, bestBid.getSide());
        assertEquals(50_000L, bestBid.getPrice());
        assertEquals(1L, bestBid.getRemainingQuantity());
    }

    /**
     * 2) 기존 SELL 위로 BUY가 들어오면 가격–시간 우선 매칭이 발생해야 한다.
     */
    @Test
    void newBuyOrder_crossesExistingSell_shouldMatchAndLeaveRestOnBook() {
        // given
        PartitionCountProvider provider = topic -> 12;
        OrderBookRegistry orderBookRegistry = new OrderBookRegistry(provider);
        MatchingEngineImpl engine = new MatchingEngineImpl(orderBookRegistry);
        long now = System.currentTimeMillis();

        OrderCommand sellCmd = OrderCommand.newOrder(
                2001L,
                "cli-sell-1",
                "BTCUSDT",
                OrderSide.SELL,
                OrderType.LIMIT,
                TimeInForce.GTC,
                BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(2),
                "TEST",
                java.time.Instant.ofEpochMilli(now)
        );
        engine.handle(sellCmd, now);

        OrderCommand buyCmd = OrderCommand.newOrder(
                1001L,
                "cli-buy-1",
                "BTCUSDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                TimeInForce.GTC,
                BigDecimal.valueOf(51_000),
                BigDecimal.valueOf(1),
                "TEST",
                java.time.Instant.ofEpochMilli(now + 1)
        );

        // when
        MatchResult result = engine.handle(buyCmd, now + 1);

        // then
        // 1) 체결 1건 기대
        assertEquals(1, result.getMatchFills().size());
        MatchFill fill = result.getMatchFills().get(0);
        assertEquals(50_000L, fill.getPrice());
        assertEquals(1L, fill.getQuantity());

        // 2) taker는 전량 체결 → 오더북 BUY 측엔 남지 않아야 함
        OrderBook book = orderBookRegistry.find("BTCUSDT").get();
        assertTrue(book.bestBid().isEmpty(), "BUY 쪽 오더는 없어야 함");

        // 3) maker는 2 중 1만 체결 → 잔량 1 남아야 함
        Optional<Order> remainingAskOpt = book.bestAsk();
        assertTrue(remainingAskOpt.isPresent());
        Order remainingAsk = remainingAskOpt.get();
        assertEquals(1L, remainingAsk.getRemainingQuantity());
        assertEquals(50_000L, remainingAsk.getPrice());
    }

    /**
     * 3) BUY taker가 여러 SELL maker를 연속으로 체결하는 케이스.
     *    - SELL 2 @ 50_000, SELL 1 @ 49_500
     *    - BUY 3 @ 51_000 → 두 레벨 모두 체결되고 오더북은 비어야 한다.
     */
    @Test
    void buyTaker_shouldMatchMultipleSellMakers_untilQuantityConsumed() {
        // given
        PartitionCountProvider provider = topic -> 12;
        OrderBookRegistry orderBookRegistry = new OrderBookRegistry(provider);
        MatchingEngineImpl engine = new MatchingEngineImpl(orderBookRegistry);
        long now = System.currentTimeMillis();

        OrderCommand sell1 = OrderCommand.newOrder(
                2001L,
                "cli-sell-1",
                "BTCUSDT",
                OrderSide.SELL,
                OrderType.LIMIT,
                TimeInForce.GTC,
                BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(2),
                "TEST",
                java.time.Instant.ofEpochMilli(now)
        );
        engine.handle(sell1, now);

        OrderCommand sell2 = OrderCommand.newOrder(
                2002L,
                "cli-sell-2",
                "BTCUSDT",
                OrderSide.SELL,
                OrderType.LIMIT,
                TimeInForce.GTC,
                BigDecimal.valueOf(49_500),
                BigDecimal.valueOf(1),
                "TEST",
                java.time.Instant.ofEpochMilli(now + 1)
        );
        engine.handle(sell2, now + 1);

        logOrderBook("Before BUY taker", engine, "BTCUSDT");

        OrderCommand buyTaker = OrderCommand.newOrder(
                1001L,
                "cli-buy-1",
                "BTCUSDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                TimeInForce.GTC,
                BigDecimal.valueOf(51_000),
                BigDecimal.valueOf(3),
                "TEST",
                java.time.Instant.ofEpochMilli(now + 2)
        );

        // when
        MatchResult result = engine.handle(buyTaker, now + 2);

        logOrderBook("After BUY taker", engine, "BTCUSDT");

        // then
        // 1) 체결은 최소 2건(두 가격 레벨) 이상이어야 한다.
        assertTrue(result.getMatchFills().size() >= 2, "두 레벨 이상 체결되어야 함");

        // 2) BUY taker는 전량 체결 → 오더북 BUY 쪽 비어야 함
        OrderBook book = orderBookRegistry.find("BTCUSDT").get();
        assertTrue(book.bestBid().isEmpty(), "BUY 쪽은 비어야 함");

        // 3) SELL 쪽도 모두 소진 → 오더북 SELL 쪽도 비어야 함
        assertTrue(book.bestAsk().isEmpty(), "SELL 쪽도 비어야 함");
    }

    /**
     * 4) 부분 체결된 주문을 CANCEL하면 남은 잔량만 오더북에서 제거되는지 확인.
     */
    @Test
    void cancelPartiallyFilledOrder_shouldRemoveRemainingQuantityFromOrderBook() {
        // given
        PartitionCountProvider provider = topic -> 12;
        OrderBookRegistry orderBookRegistry = new OrderBookRegistry(provider);
        MatchingEngineImpl engine = new MatchingEngineImpl(orderBookRegistry);
        long now = System.currentTimeMillis();

        OrderCommand sellCmd = OrderCommand.newOrder(
                2001L,
                "cli-sell-1",
                "BTCUSDT",
                OrderSide.SELL,
                OrderType.LIMIT,
                TimeInForce.GTC,
                BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(2),
                "TEST",
                java.time.Instant.ofEpochMilli(now)
        );
        engine.handle(sellCmd, now);

        OrderCommand buyCmd = OrderCommand.newOrder(
                1001L,
                "cli-buy-1",
                "BTCUSDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                TimeInForce.GTC,
                BigDecimal.valueOf(51_000),
                BigDecimal.valueOf(1),
                "TEST",
                java.time.Instant.ofEpochMilli(now + 1)
        );
        engine.handle(buyCmd, now + 1);

        logOrderBook("Before CANCEL on partially filled", engine, "BTCUSDT");

        CancelTarget target = CancelTarget.byClientOrderId("cli-sell-1");

        OrderCommand cancelCmd = OrderCommand.cancelOrder(
                2001L,
                "BTCUSDT",
                target,
                "TEST",
                java.time.Instant.ofEpochMilli(now + 2)
        );

        // when
        MatchResult cancelResult = engine.handle(cancelCmd, now + 2);

        logOrderBook("After CANCEL on partially filled", engine, "BTCUSDT");

        // then
        // 1) 취소 이벤트가 최소 1건은 있어야 한다.
        assertFalse(cancelResult.getOrderUpdates().isEmpty(), "취소 이벤트가 있어야 함");

        // 2) 해당 심볼 오더북에서 SELL 잔량이 제거되었는지 확인
        OrderBook book = orderBookRegistry.find("BTCUSDT").get();
        assertTrue(book.bestAsk().isEmpty(), "CANCEL 이후 SELL 쪽 잔량이 없어야 함");
    }

    /**
     * 5) 가격이 교차하지 않는 경우:
     *    - BUY 1 @ 50_000
     *    - SELL 1 @ 51_000
     *    → 체결 없이 각자 북에 쌓여야 한다.
     */
    @Test
    void sellAboveBestBid_shouldRestOnAskBookWithoutMatch() {
        // given
        PartitionCountProvider provider = topic -> 12;
        OrderBookRegistry orderBookRegistry = new OrderBookRegistry(provider);
        MatchingEngineImpl engine = new MatchingEngineImpl(orderBookRegistry);
        long now = System.currentTimeMillis();

        OrderCommand buy = OrderCommand.newOrder(
                1001L,
                "cli-buy-50k",
                "BTCUSDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                TimeInForce.GTC,
                BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(1),
                "TEST",
                java.time.Instant.ofEpochMilli(now)
        );
        engine.handle(buy, now);

        OrderCommand sell = OrderCommand.newOrder(
                2001L,
                "cli-sell-51k",
                "BTCUSDT",
                OrderSide.SELL,
                OrderType.LIMIT,
                TimeInForce.GTC,
                BigDecimal.valueOf(51_000),
                BigDecimal.valueOf(1),
                "TEST",
                java.time.Instant.ofEpochMilli(now + 1)
        );

        // when
        MatchResult result = engine.handle(sell, now + 1);

        // then
        assertTrue(result.getMatchFills().isEmpty(), "체결이 없어야 함");

        OrderBook book = orderBookRegistry.find("BTCUSDT").get();
        assertNotNull(book);

        // BUY는 그대로 남아야 함
        var bestBidOpt = book.bestBid();
        assertTrue(bestBidOpt.isPresent());
        Order bestBid = bestBidOpt.get();
        assertEquals(50_000L, bestBid.getPrice());
        assertEquals(1L, bestBid.getRemainingQuantity());

        // SELL도 ask로 남아야 함
        var bestAskOpt = book.bestAsk();
        assertTrue(bestAskOpt.isPresent());
        Order bestAsk = bestAskOpt.get();
        assertEquals(51_000L, bestAsk.getPrice());
        assertEquals(1L, bestAsk.getRemainingQuantity());
    }

    /**
     * 6) taker BUY 수량이 maker SELL 합보다 클 때:
     *    - SELL 1 @ 50_000
     *    - BUY 3 @ 51_000
     *    → 1개만 체결되고 BUY 잔량 2가 bid로 남아야 한다.
     */
    @Test
    void largeBuy_shouldLeaveRemainingOnBidBook() {
        // given
        PartitionCountProvider provider = topic -> 12;
        OrderBookRegistry orderBookRegistry = new OrderBookRegistry(provider);
        MatchingEngineImpl engine = new MatchingEngineImpl(orderBookRegistry);
        long now = System.currentTimeMillis();

        OrderCommand sell = OrderCommand.newOrder(
                2001L,
                "cli-sell-1",
                "BTCUSDT",
                OrderSide.SELL,
                OrderType.LIMIT,
                TimeInForce.GTC,
                BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(1),
                "TEST",
                java.time.Instant.ofEpochMilli(now)
        );
        engine.handle(sell, now);

        OrderCommand buy = OrderCommand.newOrder(
                1001L,
                "cli-buy-3",
                "BTCUSDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                TimeInForce.GTC,
                BigDecimal.valueOf(51_000),
                BigDecimal.valueOf(3),
                "TEST",
                java.time.Instant.ofEpochMilli(now + 1)
        );

        // when
        MatchResult result = engine.handle(buy, now + 1);

        // then
        // 1개만 체결
        assertEquals(1, result.getMatchFills().size());
        assertEquals(1L, result.getMatchFills().get(0).getQuantity());

        // 오더북 확인: SELL은 비어야 하고 BUY 잔량 2가 남아야 함
        OrderBook book = orderBookRegistry.find("BTCUSDT").get();
        assertTrue(book.bestAsk().isEmpty(), "SELL 쪽은 비어야 함");

        var bestBidOpt = book.bestBid();
        assertTrue(bestBidOpt.isPresent(), "BUY 잔량이 남아야 함");
        Order bestBid = bestBidOpt.get();
        assertEquals(51_000L, bestBid.getPrice());
        assertEquals(2L, bestBid.getRemainingQuantity());
    }

    /**
     * 7) 동일 가격의 SELL이 두 개 있을 때, 시간 우선(FIFO)로 먼저 들어온 주문이 먼저 체결되어야 한다.
     */
    @Test
    void samePriceSellOrders_shouldRespectTimePriority() {
        // given
        PartitionCountProvider provider = topic -> 12;
        OrderBookRegistry orderBookRegistry = new OrderBookRegistry(provider);
        MatchingEngineImpl engine = new MatchingEngineImpl(orderBookRegistry);
        long now = System.currentTimeMillis();

        OrderCommand sell1 = OrderCommand.newOrder(
                2001L,
                "cli-sell-first",
                "BTCUSDT",
                OrderSide.SELL,
                OrderType.LIMIT,
                TimeInForce.GTC,
                BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(1),
                "TEST",
                java.time.Instant.ofEpochMilli(now)
        );
        engine.handle(sell1, now);

        OrderCommand sell2 = OrderCommand.newOrder(
                2002L,
                "cli-sell-second",
                "BTCUSDT",
                OrderSide.SELL,
                OrderType.LIMIT,
                TimeInForce.GTC,
                BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(1),
                "TEST",
                java.time.Instant.ofEpochMilli(now + 1)
        );
        engine.handle(sell2, now + 1);

        OrderCommand buy = OrderCommand.newOrder(
                1001L,
                "cli-buy-1",
                "BTCUSDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                TimeInForce.GTC,
                BigDecimal.valueOf(51_000),
                BigDecimal.valueOf(1),
                "TEST",
                java.time.Instant.ofEpochMilli(now + 2)
        );

        // when
        MatchResult result = engine.handle(buy, now + 2);

        // then
        assertEquals(1, result.getMatchFills().size());
        MatchFill fill = result.getMatchFills().get(0);
        // 체결된 쪽은 accountId=2001L (첫 번째 SELL)이어야 한다.
        assertEquals(2001L, fill.getMakerAccountId());

        // 오더북에는 두 번째 SELL만 남아야 함
        OrderBook book = orderBookRegistry.find("BTCUSDT").get();
        var bestAskOpt = book.bestAsk();
        assertTrue(bestAskOpt.isPresent());
        Order remainingAsk = bestAskOpt.get();
        assertEquals(2002L, remainingAsk.getAccountId());
        assertEquals(50_000L, remainingAsk.getPrice());
        assertEquals(1L, remainingAsk.getRemainingQuantity());
    }

    /**
     * 8) 존재하지 않는 주문을 CANCEL하면 오더북에는 아무 변화가 없어야 한다.
     */
    @Test
    void cancelNonExistingOrder_shouldNotChangeOrderBook() {
        // given
        PartitionCountProvider provider = topic -> 12;
        OrderBookRegistry orderBookRegistry = new OrderBookRegistry(provider);
        MatchingEngineImpl engine = new MatchingEngineImpl(orderBookRegistry);
        long now = System.currentTimeMillis();

        OrderCommand buy = OrderCommand.newOrder(
                1001L,
                "cli-buy-1",
                "BTCUSDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                TimeInForce.GTC,
                BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(1),
                "TEST",
                java.time.Instant.ofEpochMilli(now)
        );
        engine.handle(buy, now);

        OrderBook bookBefore = orderBookRegistry.find("BTCUSDT").get();
        var bestBidBefore = bookBefore.bestBid();
        var bestAskBefore = bookBefore.bestAsk();

        CancelTarget target = CancelTarget.byClientOrderId("non-existing-cli");

        OrderCommand cancelCmd = OrderCommand.cancelOrder(
                1001L,
                "BTCUSDT",
                target,
                "TEST",
                java.time.Instant.ofEpochMilli(now + 1)
        );

        // when
        MatchResult result = engine.handle(cancelCmd, now + 1);

        // then
        // 예외 없이 지나가야 하고, 오더북 상태는 그대로여야 한다.
        OrderBook bookAfter = orderBookRegistry.find("BTCUSDT").get();

        var bestBidAfter = bookAfter.bestBid();
        var bestAskAfter = bookAfter.bestAsk();

        assertEquals(bestBidBefore.isPresent(), bestBidAfter.isPresent());
        if (bestBidBefore.isPresent()) {
            assertEquals(bestBidBefore.get().getPrice(), bestBidAfter.get().getPrice());
            assertEquals(bestBidBefore.get().getRemainingQuantity(), bestBidAfter.get().getRemainingQuantity());
        }
        assertEquals(bestAskBefore.isPresent(), bestAskAfter.isPresent());
        bestAskBefore.ifPresent(beforeAsk -> {
            Order afterAsk = bestAskAfter.orElseThrow();
            assertEquals(beforeAsk.getPrice(), afterAsk.getPrice());
            assertEquals(beforeAsk.getRemainingQuantity(), afterAsk.getRemainingQuantity());
        });
    }

    /**
     * 9) 동일한 (accountId, clientOrderId)로 NEW가 두 번 들어오더라도
     *    실제로는 한 번만 유효 주문으로 처리되어야 한다 (멱등성).
     *
     *    - BUY 1 @ 50_000 (cli-dup-1) 두 번 전송
     *    - 이후 SELL 2 @ 49_000을 보내도 체결은 1개만 일어나야 한다.
     */
    @Test
    void duplicateNewOrder_shouldBeIdempotentAndNotCreateDuplicateBookEntries() {
        // given
        PartitionCountProvider provider = topic -> 12;
        OrderBookRegistry orderBookRegistry = new OrderBookRegistry(provider);
        MatchingEngineImpl engine = new MatchingEngineImpl(orderBookRegistry);
        long now = System.currentTimeMillis();

        OrderCommand firstBuy = OrderCommand.newOrder(
                1001L,
                "cli-dup-1",
                "BTCUSDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                TimeInForce.GTC,
                BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(1),
                "TEST",
                java.time.Instant.ofEpochMilli(now)
        );
        OrderCommand duplicateBuy = OrderCommand.newOrder(
                1001L,
                "cli-dup-1",
                "BTCUSDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                TimeInForce.GTC,
                BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(1),
                "TEST",
                java.time.Instant.ofEpochMilli(now + 1)
        );

        // 첫 번째 NEW
        engine.handle(firstBuy, now);
        // 같은 clientOrderId로 두 번째 NEW (멱등성 체크 대상)
        engine.handle(duplicateBuy, now + 1);

        // 이후 SELL 2 @ 49_000을 보내면, 멱등성이 정상 동작했다면
        // BUY 1개에 대해서만 체결이 일어나야 한다.
        OrderCommand sellAggressive = OrderCommand.newOrder(
                2001L,
                "cli-sell-agg",
                "BTCUSDT",
                OrderSide.SELL,
                OrderType.LIMIT,
                TimeInForce.GTC,
                BigDecimal.valueOf(49_000),
                BigDecimal.valueOf(2),
                "TEST",
                java.time.Instant.ofEpochMilli(now + 2)
        );

        // when
        MatchResult result = engine.handle(sellAggressive, now + 2);

        // then
        assertEquals(1, result.getMatchFills().size(), "멱등성 위반 시 2건이 체결될 수 있음");
        MatchFill fill = result.getMatchFills().get(0);
        assertEquals(1L, fill.getQuantity(), "실제 유효 주문 수량(1)만 체결되어야 함");

        // 오더북 BUY는 비어 있어야 한다.
        OrderBook book = orderBookRegistry.find("BTCUSDT").get();
        assertTrue(book.bestBid().isEmpty(), "BUY 쪽은 비어 있어야 함");
    }

    private void logOrderBook(String title, MatchingEngineImpl engine, String symbol) {
        System.out.println("==== " + title + " ====");
        OrderBook book = engine.getOrderBookRegistry().find(symbol).get();
        if (book == null) {
            System.out.println("OrderBook for " + symbol + " is null");
            return;
        }
        System.out.println("BestBid: " + book.bestBid().map(o ->
                "price=" + o.getPrice() + ", qty=" + o.getRemainingQuantity()
        ).orElse("none"));
        System.out.println("BestAsk: " + book.bestAsk().map(o ->
                "price=" + o.getPrice() + ", qty=" + o.getRemainingQuantity()
        ).orElse("none"));
        System.out.println("======================");
    }
}
