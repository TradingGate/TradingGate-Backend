package org.tradinggate.backend.recon.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.tradinggate.backend.clearing.domain.ClearingBatch;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchStatus;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.clearing.repository.ClearingBatchRepository;
import org.tradinggate.backend.recon.domain.ReconBatch;
import org.tradinggate.backend.recon.domain.e.ReconFailureCode;
import org.tradinggate.backend.recon.dto.ReconDiffRow;
import org.tradinggate.backend.recon.dto.ReconRow;
import org.tradinggate.backend.recon.service.port.ReconInputsPort;
import org.tradinggate.backend.recon.service.support.ReconComparator;

import java.time.LocalDate;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
@Profile("recon")
public class ReconBatchRunner {

    private final ReconBatchService reconBatchService;
    private final ClearingBatchRepository clearingBatchRepository;

    private final ReconInputsPort reconInputsPort;
    private final ReconComparator reconComparator;
    private final ReconDiffWriter reconDiffWriter;

    /**
     * v2: clearing_batch 1건(스냅샷) 기준으로 recon 1건을 수행한다.
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

        // PENDING 선점
        boolean acquired = reconBatchService.tryMarkRunning(recon.getId());
        if (!acquired) {
            log.info("[RECON] not acquired. reconBatchId={}", recon.getId());
            return;
        }

        recon = reconBatchService.findById(recon.getId());

        try {
            // 1) snapshot(=clearing_result) 로드
            List<ReconRow> snapshot = reconInputsPort.loadSnapshot(recon);

            // 2) truth(B-1 DB) 로드
            List<ReconRow> truth = reconInputsPort.loadTruth(recon);

            // 3) compare -> diff rows 생성
            List<ReconDiffRow> diffs = reconComparator.compare(recon, truth, snapshot);

            // 4) diff 저장 (멱등)
            reconDiffWriter.upsertDiffs(recon, diffs);

            reconBatchService.markSuccess(recon.getId());
        } catch (Exception e) {
            reconBatchService.markFailed(recon.getId(), ReconFailureCode.UNEXPECTED_ERROR, summarize(e));
            throw e;
        }
    }

    /**
     * 운영 편의: 오늘 businessDate에서 "가장 최근 SUCCESS clearing batch"를 찾아 recon 수행.
     */
    public void runMostRecentSuccessClearing(LocalDate businessDate) {
        ClearingBatch clearing = clearingBatchRepository
                .findTopByBusinessDateAndBatchTypeAndScopeOrderByIdDesc(businessDate, ClearingBatchType.EOD, "")
                .orElse(null);

        if (clearing == null) {
            log.info("[RECON] no clearing batch found. date={}", businessDate);
            return;
        }
        runForClearingBatch(clearing.getId());
    }

    private String summarize(Exception e) {
        String msg = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage());
        return msg.length() <= 255 ? msg : msg.substring(0, 255);
    }
}
