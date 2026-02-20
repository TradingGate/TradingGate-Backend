package org.tradinggate.backend.clearing.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.clearing.service.port.ClearingBatchContextProvider;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Log4j2
@Component
@Profile("clearing")
public class StubClearingBatchContextProvider implements ClearingBatchContextProvider {

    private static final int DEFAULT_PARTITIONS = 3;

    @Override
    public ClearingBatchContext resolve(LocalDate businessDate, ClearingBatchType batchType, String scope) {
        Map<String, Long> watermark = deterministicWatermark(DEFAULT_PARTITIONS, businessDate, batchType);

        log.info("[CLEARING] batchContext resolved by stub. date={} type={} scope={} watermark={}",
                businessDate, batchType, scope, watermark);

        return new ClearingBatchContext(watermark);
    }

    private Map<String, Long> deterministicWatermark(int partitions, LocalDate date, ClearingBatchType type) {
        LinkedHashMap<String, Long> m = new LinkedHashMap<>();
        long base = deterministicBaseByDate(date) % 10_000;
        long typeBias = (type == ClearingBatchType.EOD) ? 100 : 10;

        for (int p = 0; p < partitions; p++) {
            m.put(String.valueOf(p), base + typeBias + p);
        }
        return m;
    }

    private long deterministicBaseByDate(LocalDate date) {
        int y = date.getYear();
        int m = date.getMonthValue();
        int d = date.getDayOfMonth();
        return (long) y * 10000 + (long) m * 100 + d;
    }
}
