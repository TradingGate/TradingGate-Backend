package org.tradinggate.backend.matching.domain.e;

public enum OrderStatus {
    NEW,
    PARTIALLY_FILLED,
    FILLED,
    CANCELED,
    REJECTED,
    EXPIRED
}
