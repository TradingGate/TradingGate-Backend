package org.tradinggate.backend.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.tradinggate.backend.global.outbox.domain.OutboxProducerType;
import org.tradinggate.backend.global.outbox.infrastructure.OutboxEventRepository;
import org.tradinggate.backend.global.outbox.service.OutboxAppender;
import org.tradinggate.backend.support.PostgresTcBase;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                // Redis / Redisson (tests shouldn't try to connect to localhost:6379)
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration," +
                "org.redisson.spring.starter.RedissonAutoConfigurationV2"

})
@ActiveProfiles("clearing")
public class OutboxAppenderIdempotencyIT extends PostgresTcBase {
    @Autowired
    OutboxAppender outboxAppender;
    @Autowired
    OutboxEventRepository outboxEventRepository;

    @Test
    void append_isIdempotentByIdempotencyKey() {
        String idemKey = "clearing:2026-01-19:EOD:1001:1";

        outboxAppender.append(
                OutboxProducerType.CLEARING,
                "CLEARING.SETTLEMENT",
                "ClearingResult",
                123L,
                idemKey,
                Map.of("k", "v")
        );

        outboxAppender.append(
                OutboxProducerType.CLEARING,
                "CLEARING.SETTLEMENT",
                "ClearingResult",
                123L,
                idemKey,
                Map.of("k", "v")
        );

        long count = outboxEventRepository.count();
        assertThat(count).isEqualTo(1L);
    }
}
