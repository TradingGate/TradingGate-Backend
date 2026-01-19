package org.tradinggate.backend.clearing.policy;

import org.springframework.stereotype.Component;
import org.tradinggate.backend.clearing.dto.ClearingScopeSpec;

import java.util.ArrayList;
import java.util.List;

@Component
public class ClearingScopeSpecParser {

    /**
     * 왜: scope는 운영/성능(NFR-C-03)의 핵심 입력이며, 잘못된 scope가 ALL로 실행되는 사고를 막기 위해 fail-fast 한다.
     * 규칙: 파싱 실패 시 IllegalArgumentException을 던져 배치를 FAILED로 종료시킨다.
     */
    public ClearingScopeSpec parse(String scope) {
        if (scope == null || scope.isBlank()) {
            return ClearingScopeSpec.all();
        }

        String s = scope.trim();

        if (s.startsWith("account:")) {
            return parseAccountRange(s.substring("account:".length()));
        }
        if (s.startsWith("symbol:")) {
            return parseSymbolSet(s.substring("symbol:".length()));
        }
        if (s.startsWith("chunk:")) {
            return parseChunk(s.substring("chunk:".length()));
        }

        throw new IllegalArgumentException("unknown scope format: " + scope);
    }

    private ClearingScopeSpec parseAccountRange(String raw) {
        String v = raw.trim();
        String[] parts = v.split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid account scope. expected account:from-to. raw=" + raw);
        }
        long from = parsePositiveLong(parts[0].trim(), "account.from");
        long to = parsePositiveLong(parts[1].trim(), "account.to");
        return ClearingScopeSpec.accountRange(from, to);
    }

    private ClearingScopeSpec parseSymbolSet(String raw) {
        String v = raw.trim();
        if (v.isEmpty()) throw new IllegalArgumentException("invalid symbol scope. empty.");
        String[] parts = v.split(",");
        List<Long> ids = new ArrayList<>(parts.length);
        for (String p : parts) {
            ids.add(parsePositiveLong(p.trim(), "symbolId"));
        }
        return ClearingScopeSpec.symbolSet(ids);
    }

    private ClearingScopeSpec parseChunk(String raw) {
        String v = raw.trim();
        String[] parts = v.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid chunk scope. expected chunk:index/total. raw=" + raw);
        }
        int index = (int) parseNonNegativeLong(parts[0].trim(), "chunk.index");
        int total = (int) parsePositiveLong(parts[1].trim(), "chunk.total");
        return ClearingScopeSpec.chunk(index, total);
    }

    private long parsePositiveLong(String s, String field) {
        try {
            long v = Long.parseLong(s);
            if (v <= 0) throw new IllegalArgumentException(field + " must be positive. value=" + s);
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " is not a number. value=" + s);
        }
    }

    private long parseNonNegativeLong(String s, String field) {
        try {
            long v = Long.parseLong(s);
            if (v < 0) throw new IllegalArgumentException(field + " must be >= 0. value=" + s);
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " is not a number. value=" + s);
        }
    }
}
