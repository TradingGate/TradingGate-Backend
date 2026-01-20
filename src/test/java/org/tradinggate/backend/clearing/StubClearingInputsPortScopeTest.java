package org.tradinggate.backend.clearing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.tradinggate.backend.clearing.policy.ClearingScopeSpecParser;
import org.tradinggate.backend.clearing.service.StubClearingInputsPort;

import static org.assertj.core.api.Assertions.assertThat;

public class StubClearingInputsPortScopeTest {

    private final ApplicationContextRunner ctx = new ApplicationContextRunner()
            .withBean(ClearingScopeSpecParser.class)
            .withBean(StubClearingInputsPort.class);

    @Test
    void scopeAll_returnsAllBaseUniverse() {
        ctx.run(context -> {
            StubClearingInputsPort port = context.getBean(StubClearingInputsPort.class);
            assertThat(port.resolveUniverse(""))
                    .hasSize(3);
        });
    }

    @Test
    void scopeAccountRange_filtersByAccountId() {
        ctx.run(context -> {
            StubClearingInputsPort port = context.getBean(StubClearingInputsPort.class);
            assertThat(port.resolveUniverse("account:1002-1002"))
                    .hasSize(1)
                    .allSatisfy(x -> assertThat(x.accountId()).isEqualTo(1002L));
        });
    }

    @Test
    void scopeSymbolSet_filtersBySymbolId() {
        ctx.run(context -> {
            StubClearingInputsPort port = context.getBean(StubClearingInputsPort.class);
            assertThat(port.resolveUniverse("symbol:2"))
                    .hasSize(1)
                    .allSatisfy(x -> assertThat(x.symbolId()).isEqualTo(2L));
        });
    }
}
