package org.tradinggate.backend.clearing.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.ClearingBatch;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchStatus;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.settlementIntegrity.clearing.repository.ClearingBatchRepository;
import org.tradinggate.backend.settlementIntegrity.clearing.service.ClearingBatchService;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClearingBatchServiceTest {

    @Mock
    private ClearingBatchRepository clearingBatchRepository;

    @InjectMocks
    private ClearingBatchService clearingBatchService;

    @Test
    void createRetryPendingFromFailed_createsNextAttempt() {
        LocalDate date = LocalDate.of(2026, 2, 23);
        ClearingBatch failed = ClearingBatch.pending(date, ClearingBatchType.EOD, "EOD", 1, "");
        setField(failed, "id", 100L);
        setField(failed, "status", ClearingBatchStatus.FAILED);

        when(clearingBatchRepository.findById(100L)).thenReturn(Optional.of(failed));
        when(clearingBatchRepository.findByBusinessDateAndBatchTypeAndRunKeyAndAttempt(date, ClearingBatchType.EOD, "EOD", 2))
                .thenReturn(Optional.empty());
        when(clearingBatchRepository.saveAndFlush(any(ClearingBatch.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ClearingBatch retry = clearingBatchService.createRetryPendingFromFailed(100L);

        assertEquals(2, retry.getAttempt());
        assertEquals(100L, retry.getRetryOfBatchId());
        assertEquals(ClearingBatchStatus.PENDING, retry.getStatus());
        assertEquals("EOD", retry.getRunKey());
    }

    @Test
    void createRetryPendingFromFailed_rejectsNonFailedSource() {
        LocalDate date = LocalDate.of(2026, 2, 23);
        ClearingBatch pending = ClearingBatch.pending(date, ClearingBatchType.EOD, "EOD", 1, "");
        setField(pending, "id", 101L);
        setField(pending, "status", ClearingBatchStatus.PENDING);

        when(clearingBatchRepository.findById(101L)).thenReturn(Optional.of(pending));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> clearingBatchService.createRetryPendingFromFailed(101L));
        assertTrue(ex.getMessage().contains("not FAILED"));
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
