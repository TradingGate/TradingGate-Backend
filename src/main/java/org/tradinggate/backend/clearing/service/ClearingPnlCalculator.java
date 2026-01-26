package org.tradinggate.backend.clearing.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.clearing.policy.SettlementPolicyResolver;
import org.tradinggate.backend.clearing.service.port.ClearingInputsPort;

import java.math.BigDecimal;

import static org.tradinggate.backend.clearing.service.port.ClearingInputsPort.*;

@Component
@Profile("clearing")
public class ClearingPnlCalculator {

    private final SettlementPolicyResolver settlementPolicyResolver;

    public ClearingPnlCalculator(SettlementPolicyResolver settlementPolicyResolver) {
        this.settlementPolicyResolver = settlementPolicyResolver;
    }

    public Result compute(
            long symbolId,
            OpeningPosition opening,
            TradeAgg agg,
            BigDecimal closingPrice
    ) {
        BigDecimal openingQty = nz(opening.openingQty());
        BigDecimal closingQty = openingQty.add(nz(agg.netQty()));

        BigDecimal openingPrice = opening.openingAvgPrice(); // null 허용(스텁 단계)
        BigDecimal realizedPnl = nz(agg.realizedPnl());

        // 공통 비용
        BigDecimal fee = nz(agg.fee());
        BigDecimal funding = nz(agg.funding());

        // 미실현 손익
        BigDecimal unrealizedPnl = computeUnrealized(openingQty, closingQty, openingPrice, closingPrice);

        // MTM 적용 여부 (EOD에서만 의미있게 쓰려면 ctx.batchType까지 받아서 추가 조건 가능)
        boolean applyMtm = settlementPolicyResolver.shouldApplyMtmToBalance(symbolId);

        // “정산 손익” 관점: applyMtm이면 realized+unrealized를 잔고에 반영, 아니면 realized만 반영(현물)
        BigDecimal pnlToBalance = applyMtm
                ? realizedPnl.add(unrealizedPnl)
                : realizedPnl;

        return new Result(
                openingQty,
                closingQty,
                openingPrice,
                closingPrice,
                realizedPnl,
                unrealizedPnl,
                fee,
                funding,
                pnlToBalance,
                applyMtm
        );
    }

    private BigDecimal computeUnrealized(
            BigDecimal openingQty,
            BigDecimal closingQty,
            BigDecimal openingPrice,
            BigDecimal closingPrice
    ) {
        if (openingPrice == null || closingPrice == null) {
            return BigDecimal.ZERO;
        }

        // - 현물/파생 모두 "현재 보유 수량 * (평가가격 - 평균단가)" 형태로 잡는다.
        // - 나중에 포지션 평균단가/체결 단가 기반으로 더 정확히 바꾸면 됨.
        return closingQty.multiply(closingPrice.subtract(openingPrice));
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    public record Result(
            BigDecimal openingQty,
            BigDecimal closingQty,
            BigDecimal openingPrice,
            BigDecimal closingPrice,
            BigDecimal realizedPnl,
            BigDecimal unrealizedPnl,
            BigDecimal fee,
            BigDecimal funding,
            BigDecimal pnlToBalance,
            boolean mtmApplied
    ) {}
}