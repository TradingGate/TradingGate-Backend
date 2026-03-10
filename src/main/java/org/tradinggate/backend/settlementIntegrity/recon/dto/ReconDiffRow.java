package org.tradinggate.backend.settlementIntegrity.recon.dto;

import org.tradinggate.backend.settlementIntegrity.recon.domain.e.ReconItemType;
import org.tradinggate.backend.settlementIntegrity.recon.domain.e.ReconSeverity;

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
