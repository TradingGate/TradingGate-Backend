package org.tradinggate.backend.clearing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tradinggate.backend.clearing.domain.ClearingBatch;
import org.tradinggate.backend.clearing.dto.ClearingResultRow;
import org.tradinggate.backend.clearing.policy.PricePolicyResolver;
import org.tradinggate.backend.clearing.service.port.ClearingCalculator;
import org.tradinggate.backend.clearing.service.port.ClearingInputsPort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.tradinggate.backend.clearing.service.port.ClearingInputsPort.*;

/**
 * Clearing 배치 1건에 대해 (accountId, symbolId) 단위 정산 결과를 계산한다.
 * 입력(포지션/체결집계/가격)은 포트로 추상화하여, 이후 실데이터 소스(DB ledger 등)로 교체해도 계산기의 흐름이 흔들리지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class DefaultClearingCalculator implements ClearingCalculator {

    private final ClearingInputsPort inputs;
    private final PricePolicyResolver pricePolicyResolver;

    /**
     * @param batch 정산 실행 단위. RUNNING 진입 시 확정된 cutoffOffsets/marketSnapshotId를 포함해야 한다.
     * @return (accountId, symbolId) 단위 정산 결과 row 목록
     * @throws IllegalStateException 정산 기준점(marketSnapshotId/cutoffOffsets) 또는 입력 포트 결과가 누락된 경우
     */
    @Override
    public List<ClearingResultRow> calculate(ClearingBatch batch) {
        LocalDate businessDate = batch.getBusinessDate();
        Long marketSnapshotId = batch.getMarketSnapshotId();
        if (marketSnapshotId == null) {
            // marketSnapshotId는 RUNNING 진입 시 확정되어야 하며, null이면 결과 재현성이 깨진다.
            throw new IllegalStateException("marketSnapshotId is null. batchId=" + batch.getId() + ", businessDate=" + businessDate + ", batchType=" + batch.getBatchType());
        }
        if (batch.getCutoffOffsets() == null) {
            // cutoffOffsets는 RUNNING 진입 시 확정되어야 하며, null이면 정합성(NFR-C-01) 검증이 불가능하다.
            throw new IllegalStateException("cutoffOffsets is null. batchId=" + batch.getId()
                    + ", businessDate=" + businessDate + ", batchType=" + batch.getBatchType());
        }

        List<AccountSymbol> universe = inputs.resolveUniverse(batch.getScope());
        if (universe == null) {
            throw new IllegalStateException("universe is null. batchId=" + batch.getId() + ", businessDate=" + businessDate + ", batchType=" + batch.getBatchType());
        }

        List<ClearingResultRow> out = new ArrayList<>(universe.size());

        for (AccountSymbol t : universe) {
            Long accountId = t.accountId();
            Long symbolId = t.symbolId();

            OpeningPosition opening = inputs.loadOpening(businessDate, accountId, symbolId);
            if (opening == null) {
                throw new IllegalStateException("opening is null. batchId=" + batch.getId()
                        + ", accountId=" + accountId + ", symbolId=" + symbolId);
            }

            TradeAgg agg = inputs.aggregateTrades(businessDate, accountId, symbolId, batch.getCutoffOffsets());
            if (agg == null) {
                throw new IllegalStateException("tradeAgg is null. batchId=" + batch.getId()
                        + ", accountId=" + accountId + ", symbolId=" + symbolId);
            }

            BigDecimal openingQty = nz(opening.openingQty());
            BigDecimal closingQty = openingQty.add(nz(agg.netQty()));

            BigDecimal openingPrice = opening.openingPrice(); // 초기엔 null 가능
            BigDecimal closingPrice = pricePolicyResolver.resolveClosingPrice(marketSnapshotId, symbolId);
            if (closingPrice == null) {
                throw new IllegalStateException("closingPrice is null. batchId=" + batch.getId()
                        + ", marketSnapshotId=" + marketSnapshotId + ", symbolId=" + symbolId);
            }

            BigDecimal realizedPnl = nz(agg.realizedPnl());
            BigDecimal fee = nz(agg.fee());
            BigDecimal funding = nz(agg.funding());

            BigDecimal unrealizedPnl = BigDecimal.ZERO;
            if (openingPrice != null) {
                // 초기 스텁 단계에서는 openingPrice가 null일 수 있어 unrealized 계산을 생략한다.
                unrealizedPnl = closingQty.multiply(closingPrice.subtract(openingPrice));
            }

            out.add(new ClearingResultRow(
                    accountId,
                    symbolId,
                    openingQty,
                    closingQty,
                    openingPrice,
                    closingPrice,
                    realizedPnl,
                    unrealizedPnl,
                    fee,
                    funding
            ));
        }

        return out;
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
