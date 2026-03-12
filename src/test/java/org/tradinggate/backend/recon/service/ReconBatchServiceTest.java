package org.tradinggate.backend.recon.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradinggate.backend.settlementIntegrity.recon.domain.ReconBatch;
import org.tradinggate.backend.settlementIntegrity.recon.repository.ReconBatchRepository;
import org.tradinggate.backend.settlementIntegrity.recon.service.ReconBatchService;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconBatchServiceTest {

    @Mock
    private ReconBatchRepository reconBatchRepository;

    @InjectMocks
    private ReconBatchService reconBatchService;

    @Test
    void getOrCreatePending_reusesLatestAttemptByDefaultPolicy() {
        ReconBatch latest = ReconBatch.pending(42L, LocalDate.of(2026, 2, 23), "WM-abc", 3);

        when(reconBatchRepository.findTopByClearingBatchIdOrderByAttemptDesc(42L))
                .thenReturn(Optional.of(latest));
        when(reconBatchRepository.findByClearingBatchIdAndAttempt(42L, 3))
                .thenReturn(Optional.of(latest));

        ReconBatch out = reconBatchService.getOrCreatePending(42L, LocalDate.of(2026, 2, 23), "WM-abc");

        assertSame(latest, out);
        assertEquals(3, out.getAttempt());
    }

    @Test
    void createRetryPendingNextAttempt_createsSeparatedAttempt() {
        ReconBatch base = ReconBatch.pending(42L, LocalDate.of(2026, 2, 23), "WM-abc", 1);
        setField(base, "id", 7L);

        when(reconBatchRepository.findById(7L)).thenReturn(Optional.of(base));
        when(reconBatchRepository.findByClearingBatchIdAndAttempt(42L, 2)).thenReturn(Optional.empty());
        when(reconBatchRepository.saveAndFlush(any(ReconBatch.class))).thenAnswer(inv -> inv.getArgument(0));

        ReconBatch retry = reconBatchService.createRetryPendingNextAttempt(7L);

        assertEquals(2, retry.getAttempt());
        assertEquals(7L, retry.getRetryOfBatchId());
        assertEquals(42L, retry.getClearingBatchId());
    }

    @Test
    void getOrCreatePendingStandalone_doesNotReuseDifferentBusinessDate_andAllocatesNextGlobalAttempt() {
        LocalDate oldDate = LocalDate.of(2026, 2, 23);
        LocalDate newDate = LocalDate.of(2026, 2, 24);

        ReconBatch oldStandalone = ReconBatch.pending(0L, oldDate, "LIVE-" + oldDate, 4);
        setField(oldStandalone, "id", 100L);

        when(reconBatchRepository.findTopByClearingBatchIdAndBusinessDateOrderByAttemptDesc(0L, newDate))
                .thenReturn(Optional.empty());
        when(reconBatchRepository.findTopByClearingBatchIdOrderByAttemptDesc(0L))
                .thenReturn(Optional.of(oldStandalone));
        when(reconBatchRepository.findByClearingBatchIdAndAttempt(0L, 5))
                .thenReturn(Optional.empty());
        when(reconBatchRepository.saveAndFlush(any(ReconBatch.class))).thenAnswer(inv -> inv.getArgument(0));

        ReconBatch created = reconBatchService.getOrCreatePendingStandalone(newDate);

        assertEquals(0L, created.getClearingBatchId());
        assertEquals(newDate, created.getBusinessDate());
        assertEquals("LIVE-" + newDate, created.getSnapshotKey());
        assertEquals(5, created.getAttempt()); // global standalone attempt namespace
    }

    @Test
    void createStandaloneRetryPendingNextAttempt_createsNewAttemptForSameDate() {
        LocalDate businessDate = LocalDate.of(2026, 2, 24);
        ReconBatch latestForDate = ReconBatch.pending(0L, businessDate, "LIVE-" + businessDate, 7);
        setField(latestForDate, "id", 701L);
        ReconBatch globalLatest = ReconBatch.pending(0L, LocalDate.of(2026, 2, 25), "LIVE-2026-02-25", 9);
        setField(globalLatest, "id", 999L);

        when(reconBatchRepository.findTopByClearingBatchIdAndBusinessDateOrderByAttemptDesc(0L, businessDate))
                .thenReturn(Optional.of(latestForDate));
        when(reconBatchRepository.findTopByClearingBatchIdOrderByAttemptDesc(0L))
                .thenReturn(Optional.of(globalLatest));
        when(reconBatchRepository.findByClearingBatchIdAndAttempt(0L, 10))
                .thenReturn(Optional.empty());
        when(reconBatchRepository.saveAndFlush(any(ReconBatch.class))).thenAnswer(inv -> inv.getArgument(0));

        ReconBatch retry = reconBatchService.createStandaloneRetryPendingNextAttempt(businessDate);

        assertEquals(0L, retry.getClearingBatchId());
        assertEquals(businessDate, retry.getBusinessDate());
        assertEquals(10, retry.getAttempt());
        assertEquals(701L, retry.getRetryOfBatchId());
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
