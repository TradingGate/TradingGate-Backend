package org.tradinggate.backend.clearing.service.port;

import org.tradinggate.backend.clearing.domain.ClearingBatch;
import org.tradinggate.backend.clearing.dto.ClearingResultRow;

import java.util.List;

public interface ClearingCalculator {

    /**
     * @return (accountId, symbolId) 단위 결과 목록
     */
    List<ClearingResultRow> calculate(ClearingBatch batch);
}
