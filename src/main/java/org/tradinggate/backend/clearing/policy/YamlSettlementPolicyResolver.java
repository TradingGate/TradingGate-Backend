package org.tradinggate.backend.clearing.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.clearing.policy.e.ClosingPriceType;
import org.tradinggate.backend.clearing.policy.e.ProductType;

@Component
@RequiredArgsConstructor
public class YamlSettlementPolicyResolver implements SettlementPolicyResolver {

    private final SettlementPolicyProperties props;

    @Override
    public ProductType productType(long symbolId) {
        return props.getSymbolProductType().getOrDefault(symbolId, props.getDefaultProductType());
    }

    @Override
    public boolean shouldApplyMtmToBalance(long symbolId) {
        Boolean override = props.getMtmOverride().get(symbolId);
        if (override != null) return override;
        return SettlementPolicyResolver.super.shouldApplyMtmToBalance(symbolId);
    }

    @Override
    public ClosingPriceType closingPriceType(long symbolId) {
        ClosingPriceType override = props.getClosingPriceOverride().get(symbolId);
        if (override != null) return override;
        return SettlementPolicyResolver.super.closingPriceType(symbolId);
    }
}
