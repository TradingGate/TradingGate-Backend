package org.tradinggate.backend.clearing.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.clearing.service.port.ClearingInputsPort;

import java.math.BigDecimal;

import static org.tradinggate.backend.clearing.service.port.ClearingInputsPort.*;

@Component
@RequiredArgsConstructor
public class DefaultPricePolicyResolver implements PricePolicyResolver {

    private final ClearingInputsPort inputs;

    @Override
    public BigDecimal resolveClosingPrice(Long marketSnapshotId, Long symbolId) {
        SymbolInfo info = inputs.loadSymbolInfo(symbolId);
        PriceSnapshot snap = inputs.loadPriceSnapshot(marketSnapshotId, symbolId);

        BigDecimal resolved = (info.productType() == ProductType.SPOT)
                ? firstNonNull(snap.close(), snap.last())
                : firstNonNull(snap.settlement(), snap.mark());

        if (resolved == null) {
            throw new IllegalStateException("Closing price resolved to null. symbolId=" + symbolId + ", snapshotId=" + marketSnapshotId);
        }
        return resolved;
    }

    private static BigDecimal firstNonNull(BigDecimal a, BigDecimal b) {
        return a != null ? a : b;
    }
}