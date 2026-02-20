package org.tradinggate.backend.clearing;

import org.junit.jupiter.api.Test;
import org.tradinggate.backend.clearing.dto.ClearingScopeSpec;
import org.tradinggate.backend.clearing.service.support.ClearingScopeSpecParser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ClearingScopeSpecParserTest {
    private final ClearingScopeSpecParser parser = new ClearingScopeSpecParser();

    @Test
    void nullOrBlank_isAll() {
        ClearingScopeSpec s1 = parser.parse(null);
        ClearingScopeSpec s2 = parser.parse("");
        ClearingScopeSpec s3 = parser.parse("   ");

        assertThat(s1.type()).isEqualTo(ClearingScopeSpec.ScopeType.ALL);
        assertThat(s2.type()).isEqualTo(ClearingScopeSpec.ScopeType.ALL);
        assertThat(s3.type()).isEqualTo(ClearingScopeSpec.ScopeType.ALL);
    }

    @Test
    void accountRange_parses() {
        ClearingScopeSpec spec = parser.parse("account:1000-1999");
        assertThat(spec.type()).isEqualTo(ClearingScopeSpec.ScopeType.ACCOUNT_RANGE);
        assertThat(spec.accountRange().fromInclusive()).isEqualTo(1000L);
        assertThat(spec.accountRange().toInclusive()).isEqualTo(1999L);
    }

    @Test
    void symbolSet_parses() {
        ClearingScopeSpec spec = parser.parse("symbol:1,2,3");
        assertThat(spec.type()).isEqualTo(ClearingScopeSpec.ScopeType.SYMBOL_SET);
        assertThat(spec.symbolIds()).containsExactly(1L, 2L, 3L);
    }

    @Test
    void chunk_parses() {
        ClearingScopeSpec spec = parser.parse("chunk:5/32");
        assertThat(spec.type()).isEqualTo(ClearingScopeSpec.ScopeType.CHUNK);
        assertThat(spec.chunk().index()).isEqualTo(5);
        assertThat(spec.chunk().total()).isEqualTo(32);
    }

    @Test
    void invalidFormat_throws() {
        assertThatThrownBy(() -> parser.parse("accounts:1-2"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("account:2"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("chunk:-1/10"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("symbol:"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
