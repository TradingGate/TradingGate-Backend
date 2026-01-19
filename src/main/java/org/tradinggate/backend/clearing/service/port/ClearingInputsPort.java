package org.tradinggate.backend.clearing.service.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ClearingInputsPort {

    /**
     * @return 정산 대상 (accountId, symbolId) 목록
     */
    List<AccountSymbol> resolveUniverse(String scope);

    /**
     * @return opening 기준값(전일 EOD 등). 없으면 0/NULL로 처리 가능.
     */
    OpeningPosition loadOpening(LocalDate businessDate, Long accountId, Long symbolId);

    /**
     * @return 당일(또는 구간) 거래 집계. cutoff 기준 반영이 필요하면 ctx를 인자로 확장 가능.
     */
    TradeAgg aggregateTrades(LocalDate businessDate, Long accountId, Long symbolId, Map<String, Long> cutoffOffsets);

    /**
     * @return 심볼 속성(현물/파생 분기)
     */
    SymbolInfo loadSymbolInfo(Long symbolId);

    /**
     * @return marketSnapshotId 기준 시세 스냅샷
     */
    PriceSnapshot loadPriceSnapshot(Long marketSnapshotId, Long symbolId);

    record AccountSymbol(Long accountId, Long symbolId) {}

    record OpeningPosition(BigDecimal openingQty, BigDecimal openingPrice) {}

    record TradeAgg(
            BigDecimal netQty,
            BigDecimal realizedPnl,
            BigDecimal fee,
            BigDecimal funding
    ) {}

    enum ProductType { SPOT, DERIVATIVE }

    record SymbolInfo(ProductType productType) {}

    record PriceSnapshot(
            BigDecimal last,
            BigDecimal close,
            BigDecimal mark,
            BigDecimal settlement
    ) {}
}
