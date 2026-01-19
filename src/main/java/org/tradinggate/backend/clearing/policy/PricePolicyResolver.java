package org.tradinggate.backend.clearing.policy;


import java.math.BigDecimal;

public interface PricePolicyResolver {
    BigDecimal resolveClosingPrice(Long marketSnapshotId, Long symbolId);
}
