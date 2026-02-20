package org.tradinggate.backend.recon.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.recon.service.ReconBatchRunner;

import java.time.LocalDate;
import java.time.ZoneId;

@Log4j2
@Component
@RequiredArgsConstructor
@Profile("recon")
public class ReconSchedule {

    private final ReconBatchRunner reconBatchRunner;

    // 예: 10분마다 "오늘 businessDate의 최신 SUCCESS clearing batch"에 대해 recon
    @Scheduled(cron = "0 */10 * * * *")
    public void runRecent() {
        reconBatchRunner.runMostRecentSuccessClearing(LocalDate.now(ZoneId.of("Asia/Seoul")));
    }
}
