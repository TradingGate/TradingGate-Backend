package org.tradinggate.backend.matching.engine.service;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.matching.engine.model.*;
import org.tradinggate.backend.matching.engine.model.e.OrderSide;
import org.tradinggate.backend.matching.engine.model.e.OrderStatus;
import org.tradinggate.backend.matching.engine.model.e.PreCheckRejectReason;
import org.tradinggate.backend.matching.engine.model.e.TimeInForce;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * - 단일 심볼(OrderBook) 단위로 주문(Command)을 적용하고,
 *   매칭 결과(체결/상태변경)를 MatchResult로 반환한다.
 *
 * [정합성/재처리]
 * - Kafka 재시도로 인해 동일 커맨드가 다시 들어올 수 있으므로,
 *   NEW에 대해서는 clientOrderId 기반 멱등성 체크를 수행한다.
 *
 * [주의]
 * - 메모리 오더북 기반이므로, 프로세스 종료/리밸런싱 시 snapshot/restore가 정합성 유지의 핵심이다.
 */
@Component
@Getter
@Profile("worker")
@RequiredArgsConstructor(access = AccessLevel.PUBLIC)
@Profile("worker")
public class MatchingEngineImpl implements MatchingEngine {

    private final OrderBookRegistry orderBookRegistry;

    @Override
    public MatchResult handle(OrderCommand command, long currentTimeMillis) {
        // TODO: 향후 여기서 공통 로깅/메트릭/트레이싱 추가
        if (command == null) {
            throw new IllegalArgumentException("OrderCommand must not be null");
        }

        if (command.getCommandType() == null) {
            throw new IllegalArgumentException("CommandType must not be null");
        }
        String symbol = command.getSymbol();
        if (symbol == null || symbol.isBlank()) {
            // TODO: 잘못된 커맨드에 대한 처리 방안 (로그만 남기고 무시 or REJECT 이벤트)
            return MatchResult.empty();
        }

        OrderBook orderBook = orderBookRegistry.getOrCreate(symbol);

        return switch (command.getCommandType()) {
            case NEW -> handleNew(orderBook, command, currentTimeMillis);
            case CANCEL -> handleCancel(orderBook, command, currentTimeMillis);
        };
    }

    /**
     * NEW 주문 처리
     */
    private MatchResult handleNew(OrderBook orderBook, OrderCommand command, long currentTimeMillis) {
        long accountId = command.getAccountId();
        String clientOrderId = command.getClientOrderId();
        String symbol = command.getSymbol();

        // Kafka 재시도(동일 레코드 재처리) 시 중복 NEW를 방지하기 위한 멱등성 규칙.
        if (isDuplicateNew(orderBook, accountId, clientOrderId)) {
            return MatchResult.empty();
        }

        // 사전 검증에서 막히면 REJECTED로 이벤트를 남기되, book에 잔존하지 않도록 한다.
        // TODO(MUST) : B 파트에서 받아와서 여기서 수정.
        PreCheckRejectReason preCheckReason = preCheckNewOrder(command);
        if (preCheckReason != null) {
            return handlePreCheckReject(orderBook, command, accountId, clientOrderId, symbol, preCheckReason, currentTimeMillis);
        }

        MatchResult result = MatchResult.empty();

        Order taker = createAndIndexNewOrder(orderBook, command, accountId, clientOrderId, symbol, currentTimeMillis, result);

        // 가격–시간 우선 매칭. (동일 가격 레벨은 FIFO)
        runMatchingLoop(orderBook, taker, symbol, currentTimeMillis, result);

        // 체결 후 잔량 처리(TIF).
        applyTimeInForceAfterMatch(orderBook, taker, currentTimeMillis, result);

        return result;
    }

    private boolean isDuplicateNew(OrderBook orderBook, long accountId, String clientOrderId) {
        if (clientOrderId == null || clientOrderId.isBlank()) {
            return false;
        }
        Optional<Order> existingOrderOpt = orderBook.findByClientOrder(accountId, clientOrderId);
        return existingOrderOpt.isPresent();
    }

    private MatchResult handlePreCheckReject(
            OrderBook orderBook,
            OrderCommand command,
            long accountId,
            String clientOrderId,
            String symbol,
            PreCheckRejectReason preCheckReason,
            long currentTimeMillis
    ) {
        MatchResult result = MatchResult.empty();

        // REJECT도 추적 가능해야 하므로 orderId를 부여하고 index에 등록
        long orderId = orderBook.nextOrderId();
        long price = command.getPrice() != null ? command.getPrice().longValue() : 0L;
        long quantity = command.getQuantity() != null ? command.getQuantity().longValue() : 0L;

        long receivedAtMillis = command.getReceivedAt() != null
                ? command.getReceivedAt().toEpochMilli()
                : currentTimeMillis;

        Order order = Order.createNew(
                orderId,
                accountId,
                clientOrderId,
                symbol,
                command.getSide(),
                command.getOrderType(),
                command.getTimeInForce(),
                price,
                quantity,
                receivedAtMillis,
                currentTimeMillis
        );

        order.reject(preCheckReason.name(), currentTimeMillis);
        orderBook.indexNewOrder(order);

        OrderUpdate update = OrderUpdate.of(
                getOrderEventId(),
                order,
                null,
                preCheckReason.name(),
                "REJECTED",
                currentTimeMillis
        );
        result.addOrderUpdate(update);

        return result;
    }

    private Order createAndIndexNewOrder(
            OrderBook orderBook,
            OrderCommand command,
            long accountId,
            String clientOrderId,
            String symbol,
            long currentTimeMillis,
            MatchResult result
    ) {
        long orderId = orderBook.nextOrderId();
        long price = toPriceLong(command.getPrice());
        long quantity = toQuantityLong(command.getQuantity());

        long receivedAtMillis = command.getReceivedAt() != null
                ? command.getReceivedAt().toEpochMilli()
                : currentTimeMillis;

        Order order = Order.createNew(
                orderId,
                accountId,
                clientOrderId,
                symbol,
                command.getSide(),
                command.getOrderType(),
                command.getTimeInForce(),
                price,
                quantity,
                receivedAtMillis,
                currentTimeMillis
        );

        orderBook.indexNewOrder(order);
        OrderUpdate createUpdate = OrderUpdate.of(
                getOrderEventId(),
                order,
                null,
                null,
                "CREATED",
                currentTimeMillis
        );
        result.addOrderUpdate(createUpdate);

        return order;
    }

    private void runMatchingLoop(
            OrderBook orderBook,
            Order taker,
            String symbol,
            long currentTimeMillis,
            MatchResult result
    ) {
        while (taker.hasRemaining()) {
            Optional<Order> bestOpt = (taker.getSide() == OrderSide.BUY) ? orderBook.bestAsk() : orderBook.bestBid();

            if (bestOpt.isEmpty()) break;

            Order maker = bestOpt.get();

            // 가격이 교차하지 않으면 더 이상 매칭 불가 → 즉시 종료.
            if (!isPriceCrossed(taker, maker)) break;

            long takerRemaining = taker.getRemainingQuantity();
            long makerRemaining = maker.getRemainingQuantity();

            // 비정상 잔량(0 이하)이 섞인 경우 book 정리 후 탈출.
            if (takerRemaining <= 0 || makerRemaining <= 0) {
                if (makerRemaining <= 0) {
                    orderBook.removeFromBook(maker);
                }
                break;
            }

            long execQuantity = Math.min(takerRemaining, makerRemaining);
            //  execPrice는 maker 가격(리미트 오더 가격) 기준으로 체결.
            long execPrice = maker.getPrice();

            OrderStatus takerPreviousStatus = taker.getStatus();
            OrderStatus makerPreviousStatus = maker.getStatus();

            long takerPrevRemaining = taker.getRemainingQuantity();
            long makerPrevRemaining = maker.getRemainingQuantity();

            taker.applyFill(execQuantity, execPrice, currentTimeMillis);
            maker.applyFill(execQuantity, execPrice, currentTimeMillis);

            // fill 후에는 레벨(totalQuantity)와 인덱스를 일관되게 갱신
            orderBook.onOrderFilled(taker, takerPrevRemaining);
            orderBook.onOrderFilled(maker, makerPrevRemaining);

            long matchId = orderBook.nextMatchId();

            MatchFill fill = MatchFill.of(matchId, symbol, execPrice, execQuantity, currentTimeMillis, taker, maker);
            result.addMatchFill(fill);

            // 상태 변화(TRADE)는 이벤트로 남겨 외부(ledger/clearing)가 재구성
            OrderUpdate takerUpdate = OrderUpdate.of(
                    getOrderEventId(),
                    taker,
                    takerPreviousStatus,
                    null,
                    "TRADE",
                    currentTimeMillis
            );
            result.addOrderUpdate(takerUpdate);

            OrderUpdate makerUpdate = OrderUpdate.of(
                    getOrderEventId(),
                    maker,
                    makerPreviousStatus,
                    null,
                    "TRADE",
                    currentTimeMillis
            );
            result.addOrderUpdate(makerUpdate);

            if (!maker.hasRemaining()) orderBook.removeFromBook(maker);
        }
    }

    private void applyTimeInForceAfterMatch(
            OrderBook orderBook,
            Order taker,
            long currentTimeMillis,
            MatchResult result
    ) {
        if (!taker.hasRemaining()) {
            return;
        }

        TimeInForce tif = taker.getTimeInForce();
        if (tif == TimeInForce.GTC) {
            // TimeInForce.GTC는 잔량을 오더북에 게시
            orderBook.addToBook(taker);
        } else {
            // TODO: IOC/FOK 등 상세 정책은 after version에서 도입.
            // GTC 이외의 잔량은 모두 취소 처리하여 book 누수를 방지
            OrderStatus previousStatus = taker.getStatus();
            taker.cancel("TIME_IN_FORCE_EXPIRED", currentTimeMillis);

            OrderUpdate tifCancelUpdate = OrderUpdate.of(
                    getOrderEventId(),
                    taker,
                    previousStatus,
                    "TIME_IN_FORCE_EXPIRED",
                    "CANCELED",
                    currentTimeMillis
            );
            result.addOrderUpdate(tifCancelUpdate);
        }
    }

    private PreCheckRejectReason preCheckNewOrder(OrderCommand command) {
        // TODO: 심볼 상태/계정 상태 캐시를 조회해서 실제로 막을지 결정
        return null;
    }

    /**
     * 가격 스케일링 헬퍼
     * - TODO: 심볼별 tickSize/scale
     */
    private long toPriceLong(BigDecimal price) {
        if (price == null) {
            return 0L;
        }
        //
        return price.longValue();
    }

    /**
     * 수량 스케일링 헬퍼
     * - TODO: lotSize/scale 적용 필요.
     */
    private long toQuantityLong(BigDecimal quantity) {
        if (quantity == null) {
            return 0L;
        }
        //
        return quantity.longValue();
    }

    /**
     * 가격 교차 여부 판단
     * - BUY taker: taker.price >= maker.price 여야 매칭
     * - SELL taker: taker.price <= maker.price 여야 매칭
     */
    private boolean isPriceCrossed(Order taker, Order maker) {
        if (taker.getSide() == OrderSide.BUY) {
            return taker.getPrice() >= maker.getPrice();
        } else if (taker.getSide() == OrderSide.SELL) {
            return taker.getPrice() <= maker.getPrice();
        }
        return false;
    }



    /**
     * CANCEL 주문 처리
     */
    private MatchResult handleCancel(OrderBook orderBook, OrderCommand command, long currentTimeMillis) {
        MatchResult result = MatchResult.empty();

        CancelTarget cancelTarget = command.getCancelTarget();
        if (cancelTarget == null || cancelTarget.getCancelBy() == null) {
            return result;
        }

        Optional<Order> targetOrderOpt = resolveCancelTarget(orderBook, command, cancelTarget);
        if (targetOrderOpt.isEmpty()) {
            return result;
        }

        Order targetOrder = targetOrderOpt.get();

        if (isTerminalStatus(targetOrder)) {
            return result;
        }

        cancelActiveOrder(orderBook, targetOrder, currentTimeMillis, result);

        return result;
    }

    private boolean isTerminalStatus(Order order) {
        OrderStatus status = order.getStatus();
        return status == OrderStatus.FILLED
                || status == OrderStatus.CANCELED
                || status == OrderStatus.REJECTED
                || status == OrderStatus.EXPIRED;
    }

    /**
     * 활성 주문에 대한 실제 취소 처리
     */
    private void cancelActiveOrder(
            OrderBook orderBook,
            Order targetOrder,
            long currentTimeMillis,
            MatchResult result
    ) {
        orderBook.removeFromBook(targetOrder);

        OrderStatus previousStatus = targetOrder.getStatus();
        targetOrder.cancel("USER_CANCEL", currentTimeMillis);

        OrderUpdate cancelUpdate = OrderUpdate.of(
                getOrderEventId(),
                targetOrder,
                previousStatus,
                "USER_CANCEL",
                "CANCELED",
                currentTimeMillis
        );
        result.addOrderUpdate(cancelUpdate);
    }

    /**
     * CancelTarget 정보를 해석해서 실제 대상 주문을 찾는다.
     */
    private Optional<Order> resolveCancelTarget(
            OrderBook orderBook,
            OrderCommand command,
            CancelTarget cancelTarget
    ) {
        String value = cancelTarget.getValue();
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        switch (cancelTarget.getCancelBy()) {
            case ORDER_ID:
                try {
                    long orderId = Long.parseLong(value);
                    Optional<Order> found = orderBook.findByOrderId(orderId);
                    return found.filter(o -> o.getAccountId() == command.getAccountId());
                } catch (NumberFormatException ex) {
                    return Optional.empty();
                }
            case CLIENT_ORDER_ID:
                long accountId = command.getAccountId();
                return orderBook.findByClientOrder(accountId, value);
            default:
                return Optional.empty();
        }
    }


    private String getOrderEventId() {
        return "orderEvent-" + UUID.randomUUID();
    }
}
