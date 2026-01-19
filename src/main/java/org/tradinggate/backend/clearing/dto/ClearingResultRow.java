package org.tradinggate.backend.clearing.dto;

import java.math.BigDecimal;

public record ClearingResultRow(
        Long accountId,
        Long symbolId,
        BigDecimal openingQty,
        BigDecimal closingQty,
        BigDecimal openingPrice,
        BigDecimal closingPrice,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal fee,
        BigDecimal funding
) {}
