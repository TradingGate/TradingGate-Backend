package org.tradinggate.backend.settlementIntegrity.recon.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.settlementIntegrity.recon.service.ReconBatchRunner;

import java.time.LocalDate;
import java.time.ZoneId;

@Log4j2
@Component
@RequiredArgsConstructor
@Profile("clearing")
@ConditionalOnProperty(prefix = "tradinggate.recon", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReconSchedule {

    private final ReconBatchRunner reconBatchRunner;

    // 스케줄 recon은 해당 businessDate의 최신 SUCCESS EOD clearing batch를 기준으로 실행한다.
    @Scheduled(cron = "${tradinggate.recon.cron:0 */10 * * * *}")
    public void runRecent() {
        reconBatchRunner.runMostRecentSuccessClearing(LocalDate.now(ZoneId.of("Asia/Seoul")));
    }
}
