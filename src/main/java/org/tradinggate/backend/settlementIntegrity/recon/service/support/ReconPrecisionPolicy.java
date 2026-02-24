package org.tradinggate.backend.settlementIntegrity.recon.service.support;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Component
public class ReconPrecisionPolicy {

    // MVP: 자산별 scale 정규화 후 exact match(허용오차 0)
    private static final Map<String, Integer> SCALE_BY_ASSET = Map.of(
            "KRW", 0,
            "USDT", 8,
            "USD", 2,
            "BTC", 8,
            "ETH", 8
    );

    private static final int DEFAULT_SCALE = 8;

    public BigDecimal normalize(String asset, BigDecimal value) {
        if (value == null) return null;
        int scale = scaleOf(asset);
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    public int scaleOf(String asset) {
        if (asset == null) return DEFAULT_SCALE;
        return SCALE_BY_ASSET.getOrDefault(asset.toUpperCase(), DEFAULT_SCALE);
    }
}
