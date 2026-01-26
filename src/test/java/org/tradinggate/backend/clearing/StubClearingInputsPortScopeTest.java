package org.tradinggate.backend.clearing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.clearing.dto.ClearingComputationContext;
import org.tradinggate.backend.clearing.dto.ClearingScopeSpec;
import org.tradinggate.backend.clearing.service.support.ClearingScopeSpecParser;
import org.tradinggate.backend.clearing.service.StubClearingInputsPort;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class StubClearingInputsPortScopeTest {

    private final ApplicationContextRunner ctx = new ApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=clearing")
            .withBean(ClearingScopeSpecParser.class)
            .withBean(StubClearingInputsPort.class);

    private ClearingComputationContext ctxOf(ClearingScopeSpec spec, String scopeRaw) {
        return new ClearingComputationContext(
                1L,
                LocalDate.of(2026, 1, 26),
                ClearingBatchType.INTRADAY,
                scopeRaw,
                spec,
                Map.of("0", 0L),
                20260126L
        );
    }

    @Test
    void scopeAll_returnsAllBaseUniverse() {
        ctx.run(context -> {
            StubClearingInputsPort port = context.getBean(StubClearingInputsPort.class);
            assertThat(port.resolveUniverse(ctxOf(ClearingScopeSpec.all(), "")))
                    .hasSize(3);
        });
    }

    @Test
    void scopeAccountRange_filtersByAccountId() {
        ctx.run(context -> {
            StubClearingInputsPort port = context.getBean(StubClearingInputsPort.class);
            assertThat(port.resolveUniverse(ctxOf(ClearingScopeSpec.accountRange(1002L, 1002L), "account:1002-1002")))
                    .hasSize(1)
                    .allSatisfy(x -> assertThat(x.accountId()).isEqualTo(1002L));
        });
    }

    @Test
    void scopeSymbolSet_filtersBySymbolId() {
        ctx.run(context -> {
            StubClearingInputsPort port = context.getBean(StubClearingInputsPort.class);
            assertThat(port.resolveUniverse(ctxOf(ClearingScopeSpec.symbolSet(java.util.List.of(2L)), "symbol:2")))
                    .hasSize(1)
                    .allSatisfy(x -> assertThat(x.symbolId()).isEqualTo(2L));
        });
    }
}
