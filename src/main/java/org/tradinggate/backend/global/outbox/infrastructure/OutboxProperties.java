package org.tradinggate.backend.global.outbox.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.tradinggate.backend.global.outbox.domain.OutboxProducerType;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "outbox")
public class OutboxProperties {

    /**
     * publishOnce()에서 한 번에 락 걸고 가져올 개수
     */
    private int batchSize = 200;

    /**
     * 실패 시 재시도 최대 횟수 (초과하면 FAILED)
     */
    private int maxRetries = 10;

    /**
     * 스케줄 폴링 주기(ms)
     */
    private long pollIntervalMs = 200;

    private Map<OutboxProducerType, Map<String, String>> topics = new HashMap<>();

    public String resolveTopic(OutboxProducerType producerType, String eventType) {
        Map<String, String> byProducer = topics.get(producerType);
        if (byProducer == null) return null;
        return byProducer.get(eventType);
    }
}
