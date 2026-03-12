package org.tradinggate.backend.recon.service.support;

import org.junit.jupiter.api.Test;
import org.tradinggate.backend.settlementIntegrity.recon.domain.ReconBatch;
import org.tradinggate.backend.settlementIntegrity.recon.dto.ReconDiffRow;
import org.tradinggate.backend.settlementIntegrity.recon.dto.ReconRow;
import org.tradinggate.backend.settlementIntegrity.recon.service.support.ReconComparator;
import org.tradinggate.backend.settlementIntegrity.recon.service.support.ReconPrecisionPolicy;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReconComparatorTest {

    @Test
    void doesNotFlagMismatchAfterAssetPrecisionNormalization() throws Exception {
        ReconComparator comparator = new ReconComparator(new ReconPrecisionPolicy());
        ReconBatch recon = stubReconBatch(1L, LocalDate.of(2026, 2, 23));

        List<ReconDiffRow> diffs = comparator.compare(
                recon,
                List.of(new ReconRow(1L, "KRW", null, new BigDecimal("100"), null, null, null, null),
                        new ReconRow(2L, "USDT", null, new BigDecimal("1.123456781"), null, null, null, null)),
                List.of(new ReconRow(1L, "KRW", null, new BigDecimal("100.4"), null, null, null, null), // KRW scale=0 => 100
                        new ReconRow(2L, "USDT", null, new BigDecimal("1.1234567806"), null, null, null, null)) // USDT scale=8 => equal
        );

        assertEquals(0, diffs.size());
    }

    @Test
    void flagsMismatchWhenNormalizedValuesStillDiffer() throws Exception {
        ReconComparator comparator = new ReconComparator(new ReconPrecisionPolicy());
        ReconBatch recon = stubReconBatch(2L, LocalDate.of(2026, 2, 23));

        List<ReconDiffRow> diffs = comparator.compare(
                recon,
                List.of(new ReconRow(1L, "USDT", null, new BigDecimal("1.00000000"), null, null, null, null)),
                List.of(new ReconRow(1L, "USDT", null, new BigDecimal("1.00010000"), null, null, null, null))
        );

        assertEquals(1, diffs.size());
        assertEquals(new BigDecimal("0.00010000"), diffs.get(0).diffValue());
    }

    @Test
    void mergesDuplicateRowsByAccountAssetBeforeComparing() throws Exception {
        ReconComparator comparator = new ReconComparator(new ReconPrecisionPolicy());
        ReconBatch recon = stubReconBatch(3L, LocalDate.of(2026, 2, 23));

        List<ReconDiffRow> diffs = comparator.compare(
                recon,
                List.of(
                        new ReconRow(1L, "usdt", null, new BigDecimal("50"), null, null, null, null),
                        new ReconRow(1L, "USDT", null, new BigDecimal("50"), null, null, null, null)
                ),
                List.of(new ReconRow(1L, "USDT", null, new BigDecimal("100"), null, null, null, null))
        );

        assertEquals(0, diffs.size());
    }

    private ReconBatch stubReconBatch(Long id, LocalDate date) throws Exception {
        ReconBatch batch = ReconBatch.pending(0L, date, "LIVE-" + date, 1);
        setField(batch, "id", id);
        return batch;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
