package org.tradinggate.backend.clearing.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.clearing.service.ClearingBatchRunner;
import org.tradinggate.backend.clearing.policy.ScheduledClearingBatchTriggerPolicy;
import org.tradinggate.backend.clearing.service.ClearingOutboxRepairService;

import java.time.LocalDate;

@Log4j2
@Component
@RequiredArgsConstructor
@Profile("clearing")
public class ClearingSchedule {

    private final ClearingBatchRunner clearingBatchRunner;
    private final ScheduledClearingBatchTriggerPolicy scheduledPolicy;
    private final ClearingOutboxRepairService clearingOutboxRepairService;

    /**
     * 스케줄 트리거는 기본적으로 전체 대상(ALL)로 실행한다.
     * scope는 null 대신 빈 문자열(=ALL)로 통일해 파싱/로그/키 생성에서 혼선을 줄인다.
     */
    private static final String DEFAULT_SCOPE = "";

    // 예시: 10분마다 Intraday
    @Scheduled(cron = "0 */10 * * * *")
    public void runIntraday() {
        clearingBatchRunner.run(LocalDate.now(), ClearingBatchType.INTRADAY, DEFAULT_SCOPE, scheduledPolicy);
    }

    // 예시: 매일 18:00 EOD (시간은 니네 시장 기준으로 조정)
    @Scheduled(cron = "0 0 18 * * *")
    public void runEod() {
        clearingBatchRunner.run(LocalDate.now(), ClearingBatchType.EOD, DEFAULT_SCOPE, scheduledPolicy);
    }

    @Scheduled(cron = "0 */5 * * * *") // 5분마다
    public void repair() {
        int repaired = clearingOutboxRepairService.repairRecentSuccessBatches();
        if (repaired > 0) {
            log.info("[CLEARING][REPAIR] repairedBatches={}", repaired);
        }
    }
}
