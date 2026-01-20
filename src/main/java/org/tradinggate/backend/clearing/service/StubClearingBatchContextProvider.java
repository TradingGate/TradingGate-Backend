package org.tradinggate.backend.clearing.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.clearing.service.port.ClearingBatchContextProvider;

import java.util.Map;

@Log4j2
@Component
@Profile("clearing")
public class StubClearingBatchContextProvider implements ClearingBatchContextProvider {

    @Override
    public ClearingBatchContext resolve(ClearingBatchType batchType, String scope) {
        // TODO(B-5): Kafka consumer group committed offsets + market snapshot 생성/조회로 교체
        log.info("[CLEARING] batchContext resolved by stub. type={} scope={}", batchType, scope);
        return new ClearingBatchContext(Map.of(), null);
    }
}
