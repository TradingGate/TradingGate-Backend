package org.tradinggate.backend.matching.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.tradinggate.backend.matching.domain.e.OrderSide;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class MatchFill {

    private final long matchId;
    private final String symbol;

    private final long price;
    private final long quantity;
    private final long executedAtMillis;

    private final long takerOrderId;
    private final long takerAccountId;
    private final OrderSide takerSide;

    private final long makerOrderId;
    private final long makerAccountId;
    private final OrderSide makerSide;

    public static MatchFill of(
            long matchId,
            String symbol,
            long price,
            long quantity,
            long executedAtMillis,
            Order taker,
            Order maker
    ) {
        return MatchFill.builder()
                .matchId(matchId)
                .symbol(symbol)
                .price(price)
                .quantity(quantity)
                .executedAtMillis(executedAtMillis)
                .takerOrderId(taker.getOrderId())
                .takerAccountId(taker.getAccountId())
                .takerSide(taker.getSide())
                .makerOrderId(maker.getOrderId())
                .makerAccountId(maker.getAccountId())
                .makerSide(maker.getSide())
                .build();
    }
}
