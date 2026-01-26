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
        Map<String, Long> cutoff = deterministicCutoff(DEFAULT_PARTITIONS, businessDate, batchType);

        long snapshotId = deterministicSnapshotIdByDate(businessDate);

        log.info("[CLEARING] batchContext resolved by stub. date={} type={} scope={} cutoff={} snapshotId={}",
                businessDate, batchType, scope, cutoff, snapshotId);

        return new ClearingBatchContext(cutoff, snapshotId);
    }

    private Map<String, Long> deterministicCutoff(int partitions, LocalDate date, ClearingBatchType type) {
        // 왜: cutoffOffsets도 재현성의 일부. 날짜/타입이 같으면 항상 같은 cutoff가 나와야 한다.
        LinkedHashMap<String, Long> m = new LinkedHashMap<>();
        long base = deterministicSnapshotIdByDate(date) % 10_000; // 너무 큰 숫자 방지용
        long typeBias = (type == ClearingBatchType.EOD) ? 100 : 10;

        for (int p = 0; p < partitions; p++) {
            // partition마다 offset이 달라서, "cutoff를 실제로 사용했는지" 로그/테스트에서 확인이 쉬워진다.
            m.put(String.valueOf(p), base + typeBias + p);
        }
        return m;
    }

    private long deterministicSnapshotIdByDate(LocalDate date) {
        int y = date.getYear();
        int m = date.getMonthValue();
        int d = date.getDayOfMonth();
        return (long) y * 10000 + (long) m * 100 + d;
    }
}
