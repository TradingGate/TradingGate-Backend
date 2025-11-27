package org.tradinggate.backend.matching.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.tradinggate.backend.matching.domain.e.CancelBy;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CancelTarget {

    private final CancelBy cancelBy;
    private final String value;

    public static CancelTarget byOrderId(long orderId) {
        return new CancelTarget(CancelBy.ORDER_ID, Long.toString(orderId));
    }

    public static CancelTarget byClientOrderId(String clientOrderId) {
        return new CancelTarget(CancelBy.CLIENT_ORDER_ID, clientOrderId);
    }
}
