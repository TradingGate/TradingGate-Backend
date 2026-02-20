package org.tradinggate.backend.clearing.service.port;

import org.tradinggate.backend.clearing.dto.ClearingComputationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ClearingInputsPort {

    /**
     * @return 정산 대상 (accountId, symbolId) 목록
     */
    List<AccountSymbol> resolveUniverse(ClearingComputationContext ctx);

    /**
     * @return opening 기준값(전일 EOD 등). 없으면 0/NULL로 처리 가능.
     */
    OpeningPosition loadOpening(ClearingComputationContext ctx, Long accountId, Long symbolId);

    /**
     * @return 당일(또는 구간) 거래 집계. cutoff 기준은 ctx.cutoffOffsets를 사용한다.
     */
    TradeAgg aggregateTrades(ClearingComputationContext ctx, Long accountId, Long symbolId);

    /**
     * @return 심볼 속성(현물/파생 분기)
     */
    SymbolInfo loadSymbolInfo(ClearingComputationContext ctx, Long symbolId);

    /**
     * @return ctx.marketSnapshotId 기준 시세 스냅샷
     */
    PriceSnapshot loadPriceSnapshot(ClearingComputationContext ctx, Long symbolId);

    record AccountSymbol(Long accountId, Long symbolId) {}

    /**
     * openingPrice = 평균단가(원가) 기준으로 보는 게 정산 관점에서 실무적.
     * prevClose 등 리포팅용 기준점이 필요하면 별도 필드로 분리 권장.
     */
    record OpeningPosition(BigDecimal openingQty, BigDecimal openingAvgPrice) {}

    record TradeAgg(BigDecimal netQty, BigDecimal realizedPnl, BigDecimal fee, BigDecimal funding) {}

    enum ProductType { SPOT, DERIVATIVE }

    record SymbolInfo(ProductType productType) {}

    record PriceSnapshot(BigDecimal last, BigDecimal close, BigDecimal mark, BigDecimal settlement) {}

    /**
     * cutoffOffsets는 "어디까지 trade를 포함할지"를 결정하는 기준점이다.
     * ledger 연동 시 aggregateTrades가 ctx.cutoffOffsets를 사용해 집계 범위를 제한해야 한다.
     */
    default Map<String, Long> cutoffOffsets(ClearingComputationContext ctx) {
        return ctx.cutoffOffsets();
    }
}
