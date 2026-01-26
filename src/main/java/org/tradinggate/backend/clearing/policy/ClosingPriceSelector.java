package org.tradinggate.backend.clearing.policy;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.clearing.policy.e.ClosingPriceType;

import java.math.BigDecimal;

import static org.tradinggate.backend.clearing.service.port.ClearingInputsPort.*;

@Component
public class ClosingPriceSelector {

    /**
     * 왜: "정책(ClosingPriceType)"과 "데이터(PriceSnapshot)"를 분리해야,
     *     정책을 바꿔도 데이터 로딩/구조가 흔들리지 않는다.
     */
    public BigDecimal select(ClosingPriceType type, PriceSnapshot snap) {
        if (snap == null) return null;

        return switch (type) {
            case CLOSE -> firstNonNull(snap.close(), snap.last());
            case LAST -> snap.last();
            case MARK -> firstNonNull(snap.mark(), snap.settlement());
            case SETTLEMENT -> snap.settlement();
        };
    }

    private static BigDecimal firstNonNull(BigDecimal a, BigDecimal b) {
        return a != null ? a : b;
    }
}
