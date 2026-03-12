package org.tradinggate.backend.settlementIntegrity.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.ClearingBatch;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchStatus;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.settlementIntegrity.clearing.policy.ManualClearingBatchTriggerPolicy;
import org.tradinggate.backend.settlementIntegrity.clearing.repository.ClearingBatchRepository;
import org.tradinggate.backend.settlementIntegrity.clearing.repository.ClearingResultRepository;
import org.tradinggate.backend.settlementIntegrity.clearing.service.ClearingBatchRunner;
import org.tradinggate.backend.settlementIntegrity.recon.domain.ReconBatch;
import org.tradinggate.backend.settlementIntegrity.recon.repository.ReconBatchRepository;
import org.tradinggate.backend.settlementIntegrity.recon.repository.ReconDiffRepository;
import org.tradinggate.backend.settlementIntegrity.recon.service.ReconBatchRunner;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/settlement")
@Profile("clearing")
public class SettlementAdminController {

    private static final String DEFAULT_SCOPE = "";
    private static final ManualClearingBatchTriggerPolicy MANUAL_POLICY = new ManualClearingBatchTriggerPolicy();

    private final ClearingBatchRunner clearingBatchRunner;
    private final ClearingBatchRepository clearingBatchRepository;
    private final ClearingResultRepository clearingResultRepository;

    private final ReconBatchRunner reconBatchRunner;
    private final ReconBatchRepository reconBatchRepository;
    private final ReconDiffRepository reconDiffRepository;

    @PostMapping("/clearing/run")
    public Map<String, Object> runClearing(
            @RequestParam(required = false) LocalDate businessDate,
            @RequestParam(defaultValue = "EOD") ClearingBatchType batchType,
            @RequestParam(defaultValue = DEFAULT_SCOPE) String scope,
            @RequestParam(defaultValue = "false") boolean forceNewRun
    ) {
        LocalDate targetDate = businessDate == null ? LocalDate.now(ZoneId.of("Asia/Seoul")) : businessDate;
        String normalizedScope = scope == null ? DEFAULT_SCOPE : scope.trim();
        String runKey = forceNewRun ? "MANUAL-" + System.currentTimeMillis() : null;

        clearingBatchRunner.run(targetDate, batchType, normalizedScope, MANUAL_POLICY, runKey);

        ClearingBatch batch = clearingBatchRepository
                .findTopByBusinessDateAndBatchTypeAndScopeOrderByIdDesc(targetDate, batchType, normalizedScope)
                .orElseThrow(() -> new IllegalStateException("ClearingBatch not found after manual run."));

        return Map.of(
                "batchId", batch.getId(),
                "businessDate", batch.getBusinessDate(),
                "batchType", batch.getBatchType(),
                "status", batch.getStatus(),
                "snapshotKey", batch.getSnapshotKey() == null ? "" : batch.getSnapshotKey(),
                "resultCount", clearingResultRepository.countByBatch_Id(batch.getId())
        );
    }

    @PostMapping("/recon/linked/run")
    public Map<String, Object> runLinkedRecon(
            @RequestParam(required = false) LocalDate businessDate,
            @RequestParam(defaultValue = "false") boolean rerun
    ) {
        LocalDate targetDate = businessDate == null ? LocalDate.now(ZoneId.of("Asia/Seoul")) : businessDate;
        if (rerun) {
            reconBatchRunner.rerunMostRecentSuccessClearingNewAttempt(targetDate);
        } else {
            reconBatchRunner.runMostRecentSuccessClearing(targetDate);
        }

        ReconBatch batch = latestReconForDate(targetDate);
        return toReconResponse(batch);
    }

    @PostMapping("/recon/standalone/run")
    public Map<String, Object> runStandaloneRecon(
            @RequestParam(required = false) LocalDate businessDate
    ) {
        LocalDate targetDate = businessDate == null ? LocalDate.now(ZoneId.of("Asia/Seoul")) : businessDate;
        reconBatchRunner.runStandalone(targetDate);

        ReconBatch batch = latestStandaloneReconForDate(targetDate);
        return toReconResponse(batch);
    }

    @PostMapping("/recon/standalone/rerun")
    public Map<String, Object> rerunStandaloneRecon(
            @RequestParam(required = false) LocalDate businessDate
    ) {
        LocalDate targetDate = businessDate == null ? LocalDate.now(ZoneId.of("Asia/Seoul")) : businessDate;
        reconBatchRunner.rerunStandaloneNewAttempt(targetDate);

        ReconBatch batch = latestStandaloneReconForDate(targetDate);
        return toReconResponse(batch);
    }

    private ReconBatch latestReconForDate(LocalDate businessDate) {
        return reconBatchRepository.findAll().stream()
                .filter(batch -> businessDate.equals(batch.getBusinessDate()))
                .max((a, b) -> Long.compare(a.getId(), b.getId()))
                .orElseThrow(() -> new IllegalStateException("ReconBatch not found after recon run."));
    }

    private ReconBatch latestStandaloneReconForDate(LocalDate businessDate) {
        return reconBatchRepository.findTopByClearingBatchIdAndBusinessDateOrderByAttemptDesc(0L, businessDate)
                .orElseThrow(() -> new IllegalStateException("Standalone ReconBatch not found after recon run."));
    }

    private Map<String, Object> toReconResponse(ReconBatch batch) {
        return Map.of(
                "reconBatchId", batch.getId(),
                "businessDate", batch.getBusinessDate(),
                "clearingBatchId", batch.getClearingBatchId(),
                "status", batch.getStatus(),
                "attempt", batch.getAttempt(),
                "diffCount", batch.getDiffCount(),
                "highSeverityCount", batch.getHighSeverityCount(),
                "totalAbsDiff", batch.getTotalAbsDiff() == null ? "0" : batch.getTotalAbsDiff(),
                "openDiffCount", reconDiffRepository.countByReconBatchId(batch.getId())
        );
    }
}
