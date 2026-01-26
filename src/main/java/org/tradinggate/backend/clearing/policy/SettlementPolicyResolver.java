package org.tradinggate.backend.clearing.policy;

import org.tradinggate.backend.clearing.policy.e.ClosingPriceType;
import org.tradinggate.backend.clearing.policy.e.ProductType;

public interface SettlementPolicyResolver {

    /**
     * 심볼의 상품 유형(현물/파생)
     */
    ProductType productType(long symbolId);

    /**
     * EOD에서 "미실현 손익을 잔고에 반영(MTM)"할지 여부
     * - 정책: SPOT = false, DERIVATIVE = true (기본)
     */
    default boolean shouldApplyMtmToBalance(long symbolId) {
        return productType(symbolId) == ProductType.DERIVATIVE;
    }

    /**
     * 정산에서 쓸 평가가격 타입
     * - 현물: CLOSE
     * - 파생: MARK
     */
    default ClosingPriceType closingPriceType(long symbolId) {
        return productType(symbolId) == ProductType.DERIVATIVE ? ClosingPriceType.MARK : ClosingPriceType.CLOSE;
    }
}
