package org.tradinggate.backend.clearing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.tradinggate.backend.clearing.dto.ClearingComputationContext;
import org.tradinggate.backend.clearing.dto.ClearingResultRow;
import org.tradinggate.backend.clearing.policy.SettlementPolicyResolver;
import org.tradinggate.backend.clearing.policy.ClosingPriceSelector;
import org.tradinggate.backend.clearing.policy.e.ClosingPriceType;
import org.tradinggate.backend.clearing.service.port.ClearingCalculator;
import org.tradinggate.backend.clearing.service.port.ClearingInputsPort;
import org.tradinggate.backend.clearing.service.port.ClearingInputsPort.AccountSymbol;
import org.tradinggate.backend.clearing.service.port.ClearingInputsPort.OpeningPosition;
import org.tradinggate.backend.clearing.service.port.ClearingInputsPort.TradeAgg;
import org.tradinggate.backend.clearing.service.port.ClearingInputsPort.PriceSnapshot;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Clearing 배치 1건에 대해 (accountId, symbolId) 단위 정산 결과를 계산한다.
 * 입력(포지션/체결집계/가격)은 포트로 추상화하여, 이후 실데이터 소스(DB ledger 등)로 교체해도 계산기의 흐름이 흔들리지 않게 한다.
 */
@Service
@RequiredArgsConstructor
@Profile("clearing")
public class DefaultClearingCalculator implements ClearingCalculator {

    private final ClearingInputsPort inputs;
    private final SettlementPolicyResolver settlementPolicyResolver;
    private final ClosingPriceSelector closingPriceSelector;
    private final ClearingPnlCalculator pnlCalculator;

    /**
     * @param ctx 정산 실행 단위. RUNNING 진입 시 확정된 cutoffOffsets/marketSnapshotId를 포함해야 한다.
     * @return (accountId, symbolId) 단위 정산 결과 row 목록
     * @throws IllegalStateException 정산 기준점(marketSnapshotId/cutoffOffsets) 또는 입력 포트 결과가 누락된 경우
     */
    @Override
    public List<ClearingResultRow> calculate(ClearingComputationContext ctx) {
        requireContext(ctx);

        List<AccountSymbol> universe = inputs.resolveUniverse(ctx);
        if (universe == null) {
            throw new IllegalStateException("universe is null. batchId=" + ctx.batchId());
        }

        List<ClearingResultRow> out = new ArrayList<>(universe.size());

        for (AccountSymbol t : universe) {
            Long accountId = t.accountId();
            Long symbolId = t.symbolId();

            OpeningPosition opening = inputs.loadOpening(ctx, accountId, symbolId);
            if (opening == null)
                throw new IllegalStateException("opening is null. batchId=" + ctx.batchId() + ", accountId=" + accountId + ", symbolId=" + symbolId);

            TradeAgg agg = inputs.aggregateTrades(ctx, accountId, symbolId);
            if (agg == null)
                throw new IllegalStateException("tradeAgg is null. batchId=" + ctx.batchId() + ", accountId=" + accountId + ", symbolId=" + symbolId);

            PriceSnapshot snap = inputs.loadPriceSnapshot(ctx, symbolId);
            if (snap == null)
                throw new IllegalStateException("priceSnapshot is null. batchId=" + ctx.batchId() + ", snapshotId=" + ctx.marketSnapshotId() + ", symbolId=" + symbolId);

            ClosingPriceType priceType = settlementPolicyResolver.closingPriceType(symbolId);
            BigDecimal closingPrice = closingPriceSelector.select(priceType, snap);
            if (closingPrice == null)
                throw new IllegalStateException("closingPrice is null. batchId=" + ctx.batchId() + ", snapshotId=" + ctx.marketSnapshotId() + ", symbolId=" + symbolId + ", type=" + priceType);

            ClearingPnlCalculator.Result r = pnlCalculator.compute(
                    symbolId,
                    opening,
                    agg,
                    closingPrice
            );

            out.add(new ClearingResultRow(
                    accountId,
                    symbolId,
                    r.openingQty(),
                    r.closingQty(),
                    r.openingPrice(),
                    r.closingPrice(),
                    r.realizedPnl(),
                    r.unrealizedPnl(),
                    r.fee(),
                    r.funding()
            ));
        }

        return out;
    }

    private void requireContext(ClearingComputationContext ctx) {
        if (ctx == null) throw new IllegalStateException("ctx is null");
        if (ctx.marketSnapshotId() == null) {
            throw new IllegalStateException("marketSnapshotId is null. batchId=" + ctx.batchId());
        }
        if (ctx.cutoffOffsets() == null || ctx.cutoffOffsets().isEmpty()) {
            throw new IllegalStateException("cutoffOffsets is empty. batchId=" + ctx.batchId());
        }
    }
}