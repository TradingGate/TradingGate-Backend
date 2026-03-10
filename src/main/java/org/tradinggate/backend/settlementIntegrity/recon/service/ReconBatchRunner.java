package org.tradinggate.backend.settlementIntegrity.recon.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.ClearingBatch;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchStatus;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.settlementIntegrity.clearing.repository.ClearingBatchRepository;
import org.tradinggate.backend.settlementIntegrity.recon.domain.ReconBatch;
import org.tradinggate.backend.settlementIntegrity.recon.domain.e.ReconFailureCode;
import org.tradinggate.backend.settlementIntegrity.recon.dto.ReconDiffRow;
import org.tradinggate.backend.settlementIntegrity.recon.dto.ReconRow;
import org.tradinggate.backend.settlementIntegrity.recon.domain.e.ReconSeverity;
import org.tradinggate.backend.settlementIntegrity.recon.repository.ReconBatchRepository;
import org.tradinggate.backend.settlementIntegrity.recon.service.port.ReconInputsPort;
import org.tradinggate.backend.settlementIntegrity.recon.service.support.ReconComparator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
@Profile("clearing")
public class ReconBatchRunner {

    private final ReconBatchService reconBatchService;
    private final ClearingBatchRepository clearingBatchRepository;
    private final ReconBatchRepository reconBatchRepository;

    private final ReconInputsPort reconInputsPort;
    private final ReconComparator reconComparator;
    private final ReconDiffWriter reconDiffWriter;

    /**
     * SUCCESS 상태의 clearing batch를 기준으로 recon을 수행한다. (linked mode)
     */
    public void runForClearingBatch(Long clearingBatchId) {
        ClearingBatch clearing = clearingBatchRepository.findById(clearingBatchId)
                .orElseThrow(() -> new IllegalStateException("ClearingBatch not found. id=" + clearingBatchId));

        if (clearing.getStatus() != ClearingBatchStatus.SUCCESS) {
            log.info("[RECON] skipped. clearingBatch not SUCCESS. id={} status={}", clearingBatchId, clearing.getStatus());
            return;
        }

        ReconBatch recon = reconBatchService.getOrCreatePending(
                clearing.getId(),
                clearing.getBusinessDate(),
                clearing.getSnapshotKey()
        );

        boolean acquired = reconBatchService.tryMarkRunning(recon.getId());
        if (!acquired) {
            log.info("[RECON] not acquired. reconBatchId={}", recon.getId());
            return;
        }

        recon = reconBatchService.findById(recon.getId());

        try {
            List<ReconRow> snapshot = reconInputsPort.loadSnapshot(recon);
            List<ReconRow> truth = reconInputsPort.loadTruth(recon);
            List<ReconDiffRow> diffs = reconComparator.compare(recon, truth, snapshot);
            reconDiffWriter.upsertDiffs(recon, diffs);
            markSuccessWithSummary(recon, diffs);
        } catch (Exception e) {
            reconBatchService.markFailed(recon.getId(), ReconFailureCode.UNEXPECTED_ERROR, summarize(e));
            throw e;
        }
    }

    /**
     * linked recon 수동 재실행 경로. 같은 clearing batch에 대해 새 attempt를 생성한다.
     */
    public void rerunForClearingBatchNewAttempt(Long clearingBatchId) {
        ClearingBatch clearing = clearingBatchRepository.findById(clearingBatchId)
                .orElseThrow(() -> new IllegalStateException("ClearingBatch not found. id=" + clearingBatchId));

        if (clearing.getStatus() != ClearingBatchStatus.SUCCESS) {
            log.info("[RECON] rerun skipped. clearingBatch not SUCCESS. id={} status={}", clearingBatchId, clearing.getStatus());
            return;
        }

        ReconBatch latest = reconBatchRepository.findTopByClearingBatchIdOrderByAttemptDesc(clearingBatchId).orElse(null);
        if (latest == null) {
            runForClearingBatch(clearingBatchId);
            return;
        }

        ReconBatch recon = reconBatchService.createRetryPendingNextAttempt(latest.getId());
        runLinkedInternal(recon);
    }

    /**
     * 스케줄 편의용 진입점: 해당 businessDate의 최신 SUCCESS EOD clearing batch를 사용한다.
     */
    public void runMostRecentSuccessClearing(LocalDate businessDate) {
        ClearingBatch clearing = clearingBatchRepository
                .findTopByBusinessDateAndBatchTypeAndStatusAndScopeOrderByIdDesc(
                        businessDate,
                        ClearingBatchType.EOD,
                        ClearingBatchStatus.SUCCESS,
                        ""
                )
                .orElse(null);

        if (clearing == null) {
            log.info("[RECON] no clearing batch found. fallback to standalone live recon. date={}", businessDate);
            runStandalone(businessDate);
            return;
        }
        runForClearingBatch(clearing.getId());
    }

    public void rerunMostRecentSuccessClearingNewAttempt(LocalDate businessDate) {
        ClearingBatch clearing = clearingBatchRepository
                .findTopByBusinessDateAndBatchTypeAndStatusAndScopeOrderByIdDesc(
                        businessDate,
                        ClearingBatchType.EOD,
                        ClearingBatchStatus.SUCCESS,
                        ""
                )
                .orElse(null);

        if (clearing == null) {
            log.info("[RECON] rerun skipped. no SUCCESS clearing batch found. date={}", businessDate);
            return;
        }
        rerunForClearingBatchNewAttempt(clearing.getId());
    }

    /**
     * standalone 모드: clearing batch 없이 live projection(account_balance)과 ledger truth를 직접 비교한다.
     */
    public void runStandalone(LocalDate businessDate) {
        ReconBatch recon = reconBatchService.getOrCreatePendingStandalone(businessDate);
        runStandaloneInternal(recon, "standalone");
    }

    /**
     * standalone 수동 재실행 경로. 같은 날짜에 대해서도 항상 새 attempt를 생성한다.
     */
    public void rerunStandaloneNewAttempt(LocalDate businessDate) {
        ReconBatch recon = reconBatchService.createStandaloneRetryPendingNextAttempt(businessDate);
        runStandaloneInternal(recon, "standalone-rerun");
    }

    private void runStandaloneInternal(ReconBatch recon, String mode) {
        boolean acquired = reconBatchService.tryMarkRunning(recon.getId());
        if (!acquired) {
            log.info("[RECON] {} not acquired. reconBatchId={}", mode, recon.getId());
            return;
        }

        recon = reconBatchService.findById(recon.getId());

        try {
            // v1에서는 snapshot/truth를 모두 DB 테이블에서 직접 로드한다.
            List<ReconRow> snapshot = reconInputsPort.loadSnapshot(recon);
            List<ReconRow> truth = reconInputsPort.loadTruth(recon);
            List<ReconDiffRow> diffs = reconComparator.compare(recon, truth, snapshot);
            reconDiffWriter.upsertDiffs(recon, diffs);
            markSuccessWithSummary(recon, diffs);
        } catch (Exception e) {
            reconBatchService.markFailed(recon.getId(), ReconFailureCode.UNEXPECTED_ERROR, summarize(e));
            throw e;
        }
    }

    private void runLinkedInternal(ReconBatch recon) {
        boolean acquired = reconBatchService.tryMarkRunning(recon.getId());
        if (!acquired) {
            log.info("[RECON] linked not acquired. reconBatchId={}", recon.getId());
            return;
        }

        recon = reconBatchService.findById(recon.getId());

        try {
            List<ReconRow> snapshot = reconInputsPort.loadSnapshot(recon);
            List<ReconRow> truth = reconInputsPort.loadTruth(recon);
            List<ReconDiffRow> diffs = reconComparator.compare(recon, truth, snapshot);
            reconDiffWriter.upsertDiffs(recon, diffs);
            markSuccessWithSummary(recon, diffs);
        } catch (Exception e) {
            reconBatchService.markFailed(recon.getId(), ReconFailureCode.UNEXPECTED_ERROR, summarize(e));
            throw e;
        }
    }

    private void markSuccessWithSummary(ReconBatch recon, List<ReconDiffRow> diffs) {
        long diffCount = diffs == null ? 0L : diffs.size();
        long highSeverityCount = diffs == null ? 0L : diffs.stream()
                .filter(d -> d.severity() == ReconSeverity.HIGH || d.severity() == ReconSeverity.CRITICAL)
                .count();
        BigDecimal totalAbsDiff = diffs == null ? BigDecimal.ZERO : diffs.stream()
                .map(ReconDiffRow::diffValue)
                .filter(v -> v != null)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        reconBatchService.markSuccessWithSummary(recon.getId(), diffCount, highSeverityCount, totalAbsDiff);
    }

    private String summarize(Exception e) {
        String msg = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage());
        return msg.length() <= 255 ? msg : msg.substring(0, 255);
    }
}
