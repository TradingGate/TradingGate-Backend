package org.tradinggate.backend.clearing.service;

import org.junit.jupiter.api.Test;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.settlementIntegrity.clearing.dto.ClearingComputationContext;
import org.tradinggate.backend.settlementIntegrity.clearing.dto.ClearingResultRow;
import org.tradinggate.backend.settlementIntegrity.clearing.service.DefaultClearingCalculator;
import org.tradinggate.backend.settlementIntegrity.clearing.service.port.ClearingInputsPort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultClearingCalculatorTest {

    @Test
    void fallbackOpeningUsesClosingMinusPeriodNetChange() {
        DefaultClearingCalculator calculator = new DefaultClearingCalculator(new FixedInputs(
                List.of(new ClearingInputsPort.AccountAsset(1001L, "USDT")),
                null,
                new ClearingInputsPort.BalanceSnapshot(bd("150"), bd("150"), BigDecimal.ZERO),
                new ClearingInputsPort.LedgerAgg(bd("50"), bd("2"), 1L, bd("100"))
        ));

        List<ClearingResultRow> rows = calculator.calculate(ctx());

        assertEquals(1, rows.size());
        ClearingResultRow row = rows.get(0);
        assertEquals(bd("100"), row.openingBalance());
        assertEquals(bd("150"), row.closingBalance());
        assertEquals(bd("50"), row.netChange());
    }

    @Test
    void usesProvidedOpeningBalanceWhenPresent() {
        DefaultClearingCalculator calculator = new DefaultClearingCalculator(new FixedInputs(
                List.of(new ClearingInputsPort.AccountAsset(1001L, "USDT")),
                new ClearingInputsPort.BalanceSnapshot(bd("120"), bd("120"), BigDecimal.ZERO),
                new ClearingInputsPort.BalanceSnapshot(bd("150"), bd("150"), BigDecimal.ZERO),
                new ClearingInputsPort.LedgerAgg(bd("30"), bd("1"), 1L, bd("50"))
        ));

        List<ClearingResultRow> rows = calculator.calculate(ctx());

        assertEquals(1, rows.size());
        assertEquals(bd("120"), rows.get(0).openingBalance());
        assertEquals(bd("30"), rows.get(0).netChange());
    }

    @Test
    void skipsZeroNoiseRows() {
        DefaultClearingCalculator calculator = new DefaultClearingCalculator(new FixedInputs(
                List.of(new ClearingInputsPort.AccountAsset(1001L, "USDT")),
                null,
                new ClearingInputsPort.BalanceSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new ClearingInputsPort.LedgerAgg(BigDecimal.ZERO, BigDecimal.ZERO, 0L, BigDecimal.ZERO)
        ));

        List<ClearingResultRow> rows = calculator.calculate(ctx());

        assertTrue(rows.isEmpty());
    }

    @Test
    void throwsWhenOpeningPlusNetDoesNotMatchClosing() {
        DefaultClearingCalculator calculator = new DefaultClearingCalculator(new FixedInputs(
                List.of(new ClearingInputsPort.AccountAsset(1001L, "USDT")),
                new ClearingInputsPort.BalanceSnapshot(bd("10"), bd("10"), BigDecimal.ZERO),
                new ClearingInputsPort.BalanceSnapshot(bd("20"), bd("20"), BigDecimal.ZERO),
                new ClearingInputsPort.LedgerAgg(bd("5"), BigDecimal.ZERO, 0L, BigDecimal.ZERO)
        ));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> calculator.calculate(ctx()));
        assertTrue(ex.getMessage().contains("opening+net!=closing"));
    }

    @Test
    void throwsWhenTradeCountZeroButTradeValueNonZero() {
        DefaultClearingCalculator calculator = new DefaultClearingCalculator(new FixedInputs(
                List.of(new ClearingInputsPort.AccountAsset(1001L, "USDT")),
                null,
                new ClearingInputsPort.BalanceSnapshot(bd("100"), bd("100"), BigDecimal.ZERO),
                new ClearingInputsPort.LedgerAgg(BigDecimal.ZERO, BigDecimal.ZERO, 0L, bd("10"))
        ));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> calculator.calculate(ctx()));
        assertTrue(ex.getMessage().contains("tradeCount==0 but tradeValue!=0"));
    }

    private ClearingComputationContext ctx() {
        return new ClearingComputationContext(
                1L,
                "WM-test",
                LocalDate.of(2026, 2, 23),
                ClearingBatchType.EOD,
                Map.of("max_ledger_id", 123L),
                null
        );
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private record FixedInputs(
            List<ClearingInputsPort.AccountAsset> universe,
            ClearingInputsPort.BalanceSnapshot opening,
            ClearingInputsPort.BalanceSnapshot closing,
            ClearingInputsPort.LedgerAgg agg
    ) implements ClearingInputsPort {
        @Override
        public List<AccountAsset> resolveUniverse(ClearingComputationContext ctx) { return universe; }

        @Override
        public BalanceSnapshot loadOpeningBalance(ClearingComputationContext ctx, Long accountId, String asset) { return opening; }

        @Override
        public BalanceSnapshot loadClosingBalance(ClearingComputationContext ctx, Long accountId, String asset) { return closing; }

        @Override
        public LedgerAgg aggregateLedger(ClearingComputationContext ctx, Long accountId, String asset) { return agg; }
    }
}
