package org.tradinggate.backend.recon.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.ClearingBatch;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchStatus;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.settlementIntegrity.clearing.repository.ClearingBatchRepository;
import org.tradinggate.backend.settlementIntegrity.recon.domain.ReconBatch;
import org.tradinggate.backend.settlementIntegrity.recon.domain.e.ReconItemType;
import org.tradinggate.backend.settlementIntegrity.recon.domain.e.ReconSeverity;
import org.tradinggate.backend.settlementIntegrity.recon.dto.ReconDiffRow;
import org.tradinggate.backend.settlementIntegrity.recon.dto.ReconRow;
import org.tradinggate.backend.settlementIntegrity.recon.service.ReconBatchRunner;
import org.tradinggate.backend.settlementIntegrity.recon.service.ReconBatchService;
import org.tradinggate.backend.settlementIntegrity.recon.service.ReconDiffWriter;
import org.tradinggate.backend.settlementIntegrity.recon.service.port.ReconInputsPort;
import org.tradinggate.backend.settlementIntegrity.recon.service.support.ReconComparator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconBatchRunnerTest {

    @Mock
    private ReconBatchService reconBatchService;
    @Mock
    private ClearingBatchRepository clearingBatchRepository;
    @Mock
    private ReconInputsPort reconInputsPort;
    @Mock
    private ReconComparator reconComparator;
    @Mock
    private ReconDiffWriter reconDiffWriter;

    @InjectMocks
    private ReconBatchRunner runner;

    @Test
    void runStandaloneStoresSummary() {
        ReconBatch pending = mock(ReconBatch.class);
        ReconBatch running = mock(ReconBatch.class);
        when(pending.getId()).thenReturn(10L);
        when(running.getId()).thenReturn(10L);

        when(reconBatchService.getOrCreatePendingStandalone(any())).thenReturn(pending);
        when(reconBatchService.tryMarkRunning(10L)).thenReturn(true);
        when(reconBatchService.findById(10L)).thenReturn(running);
        when(reconInputsPort.loadSnapshot(running)).thenReturn(List.of(new ReconRow(1L, "USDT", null, bd("100"), null, null, null, null)));
        when(reconInputsPort.loadTruth(running)).thenReturn(List.of(new ReconRow(1L, "USDT", null, bd("90"), null, null, null, null)));

        List<ReconDiffRow> diffs = List.of(
                new ReconDiffRow(10L, LocalDate.of(2026, 2, 23), 1L, "USDT",
                        ReconItemType.CLOSING_BALANCE, bd("90"), bd("100"), bd("10"), ReconSeverity.HIGH, "x"),
                new ReconDiffRow(10L, LocalDate.of(2026, 2, 23), 2L, "BTC",
                        ReconItemType.CLOSING_BALANCE, bd("1"), bd("0.9"), bd("-0.1"), ReconSeverity.MEDIUM, "x")
        );
        when(reconComparator.compare(eq(running), anyList(), anyList())).thenReturn(diffs);

        runner.runStandalone(LocalDate.of(2026, 2, 23));

        verify(reconDiffWriter).upsertDiffs(running, diffs);
        verify(reconBatchService).markSuccessWithSummary(10L, 2L, 1L, bd("10.1"));
    }

    @Test
    void runMostRecentSuccessClearingFallsBackToStandaloneWhenNoClearingBatch() {
        when(clearingBatchRepository.findTopByBusinessDateAndBatchTypeAndScopeOrderByIdDesc(any(), eq(ClearingBatchType.EOD), eq("")))
                .thenReturn(Optional.empty());

        ReconBatch pending = mock(ReconBatch.class);
        ReconBatch running = mock(ReconBatch.class);
        when(pending.getId()).thenReturn(11L);
        when(running.getId()).thenReturn(11L);

        when(reconBatchService.getOrCreatePendingStandalone(any())).thenReturn(pending);
        when(reconBatchService.tryMarkRunning(11L)).thenReturn(true);
        when(reconBatchService.findById(11L)).thenReturn(running);
        when(reconInputsPort.loadSnapshot(running)).thenReturn(List.of());
        when(reconInputsPort.loadTruth(running)).thenReturn(List.of());
        when(reconComparator.compare(eq(running), anyList(), anyList())).thenReturn(List.of());

        runner.runMostRecentSuccessClearing(LocalDate.of(2026, 2, 23));

        verify(reconBatchService).getOrCreatePendingStandalone(LocalDate.of(2026, 2, 23));
        verify(reconBatchService).markSuccessWithSummary(11L, 0L, 0L, BigDecimal.ZERO);
    }

    @Test
    void runForClearingBatchSkipsWhenClearingNotSuccess() {
        ClearingBatch clearing = mock(ClearingBatch.class);
        when(clearing.getStatus()).thenReturn(ClearingBatchStatus.FAILED);
        when(clearingBatchRepository.findById(99L)).thenReturn(Optional.of(clearing));

        runner.runForClearingBatch(99L);

        verifyNoInteractions(reconBatchService);
        verifyNoInteractions(reconDiffWriter);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
