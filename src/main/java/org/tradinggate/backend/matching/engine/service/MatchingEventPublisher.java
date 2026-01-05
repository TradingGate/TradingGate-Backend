package org.tradinggate.backend.matching.engine.service;

import org.tradinggate.backend.matching.engine.model.MatchFill;
import org.tradinggate.backend.matching.engine.model.OrderUpdate;

import java.util.List;

public interface MatchingEventPublisher {

    /**
     * 매칭 엔진에서 나온 주문 상태 변경 결과를 외부로 발행.
     * 향후 Kafka orders.updated 이벤트로 변환하는 역할은 구현체에서 담당.
     */
    void publishOrderUpdates(
            String symbol,
            List<OrderUpdate> updates,
            String sourceTopic,
            int sourcePartition,
            long sourceOffset
    );

    /**
     * 매칭 엔진에서 나온 체결 결과를 외부로 발행.
     * 향후 Kafka trades.executed 이벤트로 변환하는 역할은 구현체에서 담당.
     */
    void publishMatchFills(
            String symbol,
            List<MatchFill> fills,
            String sourceTopic,
            int sourcePartition,
            long sourceOffset
    );
}
