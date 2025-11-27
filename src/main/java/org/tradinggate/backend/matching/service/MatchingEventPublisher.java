package org.tradinggate.backend.matching.service;

import org.tradinggate.backend.matching.domain.MatchFill;
import org.tradinggate.backend.matching.domain.OrderUpdate;

import java.util.List;

public interface MatchingEventPublisher {

    /**
     * 매칭 엔진에서 나온 주문 상태 변경 결과를 외부로 발행.
     * 향후 Kafka orders.updated 이벤트로 변환하는 역할은 구현체에서 담당.
     */
    void publishOrderUpdates(String symbol, List<OrderUpdate> updates);

    /**
     * 매칭 엔진에서 나온 체결 결과를 외부로 발행.
     * 향후 Kafka trades.executed 이벤트로 변환하는 역할은 구현체에서 담당.
     */
    void publishMatchFills(String symbol, List<MatchFill> fills);
}
