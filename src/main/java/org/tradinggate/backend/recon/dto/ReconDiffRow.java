package org.tradinggate.backend.recon.dto;

import org.tradinggate.backend.recon.domain.e.ReconItemType;
import org.tradinggate.backend.recon.domain.e.ReconSeverity;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReconDiffRow(
        Long reconBatchId,
        LocalDate businessDate,
        Long accountId,
        String asset,
        ReconItemType itemType,
        BigDecimal expectedValue,
        BigDecimal actualValue,
        BigDecimal diffValue,
        ReconSeverity severity,
        String memo
) {}
