package org.tradinggate.backend.settlementIntegrity.clearing.dto;

import java.math.BigDecimal;

public record ClearingResultRow(
        Long accountId,
        String asset,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal netChange,
        BigDecimal feeTotal,
        long tradeCount,
        BigDecimal tradeValue
) {}
