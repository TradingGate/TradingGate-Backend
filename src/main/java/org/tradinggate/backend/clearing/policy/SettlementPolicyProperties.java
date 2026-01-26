package org.tradinggate.backend.clearing.policy;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.tradinggate.backend.clearing.policy.e.ClosingPriceType;
import org.tradinggate.backend.clearing.policy.e.ProductType;

import java.util.HashMap;
import java.util.Map;


@Getter
@Setter
@ConfigurationProperties(prefix = "settlement.policy")
public class SettlementPolicyProperties {

    /**
     * symbolId -> productType
     * 예: { 1: "SPOT", 2: "DERIVATIVE" }
     */
    private Map<Long, ProductType> symbolProductType = new HashMap<>();

    /**
     * 기본값: 심볼이 매핑 안 되어있을 때 어떤 타입으로 볼지
     * 안전하게 하려면 SPOT 권장 (MTM이 돈에 영향을 주기 때문)
     */
    private ProductType defaultProductType = ProductType.SPOT;

    /**
     * 특정 심볼만 MTM 강제 ON/OFF 예외 처리
     */
    private Map<Long, Boolean> mtmOverride = new HashMap<>();

    /**
     * 특정 심볼 closing price type 예외 처리
     */
    private Map<Long, ClosingPriceType> closingPriceOverride = new HashMap<>();
}
