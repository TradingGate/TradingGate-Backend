package org.tradinggate.backend.recon.service.support;

import org.junit.jupiter.api.Test;
import org.tradinggate.backend.settlementIntegrity.recon.service.support.ReconPrecisionPolicy;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReconPrecisionPolicyTest {

    private final ReconPrecisionPolicy policy = new ReconPrecisionPolicy();

    @Test
    void normalizesKrwToScaleZero() {
        assertEquals(new BigDecimal("100"), policy.normalize("KRW", new BigDecimal("100.4")));
        assertEquals(new BigDecimal("101"), policy.normalize("KRW", new BigDecimal("100.5")));
    }

    @Test
    void normalizesUsdtToScaleEight() {
        assertEquals(new BigDecimal("1.12345679"), policy.normalize("USDT", new BigDecimal("1.123456786")));
    }

    @Test
    void usesDefaultScaleForUnknownAsset() {
        assertEquals(new BigDecimal("9.87654321"), policy.normalize("ABC", new BigDecimal("9.876543214")));
    }
}
