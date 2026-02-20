package org.tradinggate.backend.recon.dto;

import java.math.BigDecimal;

public record ReconRow(
        Long accountId,
        String asset,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal netChange,
        BigDecimal feeTotal,
        Long tradeCount,
        BigDecimal tradeValue
) {}
