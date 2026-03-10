package org.tradinggate.backend.settlementIntegrity.clearing.service.port;

import org.tradinggate.backend.settlementIntegrity.clearing.dto.ClearingComputationContext;
import org.tradinggate.backend.settlementIntegrity.clearing.dto.ClearingResultRow;

import java.util.List;

public interface ClearingCalculator {

    /**
     * @return (accountId, symbolId) 단위 결과 목록
     */
    List<ClearingResultRow> calculate(ClearingComputationContext ctx);
}
