package org.tradinggate.backend.settlementIntegrity.clearing.service.port;

import org.tradinggate.backend.settlementIntegrity.clearing.dto.ClearingComputationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ClearingInputsPort {

    /**
     * @return scope 반영된 정산 대상 (accountId, asset) 목록
     */
    List<AccountAsset> resolveUniverse(ClearingComputationContext ctx);

    /**
     * @return opening 기준값(전일 EOD FINAL 결과 등). 없으면 null 허용(MVP)
     */
    BalanceSnapshot loadOpeningBalance(ClearingComputationContext ctx, Long accountId, String asset);

    /**
     * @return closing 기준값(워터마크 이하 ledger 누적합 기반 스냅샷).
     */
    BalanceSnapshot loadClosingBalance(ClearingComputationContext ctx, Long accountId, String asset);

    /**
     * @return 당일(또는 구간) 거래 요약 집계.
     * - feeTotal/tradeCount/tradeValue 산출
     * - 집계 범위 제한이 필요하면 ctx.cutoffOffsets를 사용한다.
     */
    LedgerAgg aggregateLedger(ClearingComputationContext ctx, Long accountId, String asset);

    record AccountAsset(Long accountId, String asset) {}

    record BalanceSnapshot(
            BigDecimal totalBalance,
            BigDecimal availableBalance,
            BigDecimal lockedBalance
    ) {}

    record LedgerAgg(
            BigDecimal periodNetChange,
            BigDecimal feeTotal,
            long tradeCount,
            BigDecimal tradeValue
    ) {}

    /**
     * cutoffOffsets는 "어디까지 반영된 trade/ledger를 포함할지"를 결정하는 기준점이다.
     * - v2에서는 calculator가 아닌 inputs 구현이 필요 시 이를 사용해 범위를 제한한다.
     */
    default Map<String, Long> cutoffOffsets(ClearingComputationContext ctx) {
        return ctx.cutoffOffsets();
    }
}
