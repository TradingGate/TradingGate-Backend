package org.tradinggate.backend.recon.service.support;

import org.springframework.stereotype.Component;
import org.tradinggate.backend.recon.domain.ReconBatch;
import org.tradinggate.backend.recon.domain.e.ReconItemType;
import org.tradinggate.backend.recon.domain.e.ReconSeverity;
import org.tradinggate.backend.recon.dto.ReconDiffRow;
import org.tradinggate.backend.recon.dto.ReconRow;

import java.math.BigDecimal;
import java.util.*;

@Component
public class ReconComparator {

    public List<ReconDiffRow> compare(ReconBatch recon, List<ReconRow> truth, List<ReconRow> snapshot) {
        Map<String, ReconRow> t = index(truth);
        Map<String, ReconRow> s = index(snapshot);

        // union key
        Set<String> keys = new HashSet<>();
        keys.addAll(t.keySet());
        keys.addAll(s.keySet());

        List<ReconDiffRow> diffs = new ArrayList<>();

        for (String key : keys) {
            ReconRow tr = t.get(key);
            ReconRow sr = s.get(key);

            Long accountId = (tr != null) ? tr.accountId() : sr.accountId();
            String asset = (tr != null) ? tr.asset() : sr.asset();

            // v2에서 최소 핵심: closing_balance / fee_total / trade_count / trade_value
            addIfMismatch(diffs, recon, accountId, asset, ReconItemType.CLOSING_BALANCE,
                    val(tr == null ? null : tr.closingBalance()),
                    val(sr == null ? null : sr.closingBalance()));

            addIfMismatch(diffs, recon, accountId, asset, ReconItemType.FEE_TOTAL,
                    val(tr == null ? null : tr.feeTotal()),
                    val(sr == null ? null : sr.feeTotal()));

            addIfMismatch(diffs, recon, accountId, asset, ReconItemType.TRADE_COUNT,
                    bd(tr == null ? null : tr.tradeCount()),
                    bd(sr == null ? null : sr.tradeCount()));

            addIfMismatch(diffs, recon, accountId, asset, ReconItemType.TRADE_VALUE,
                    val(tr == null ? null : tr.tradeValue()),
                    val(sr == null ? null : sr.tradeValue()));
        }

        return diffs;
    }

    private void addIfMismatch(List<ReconDiffRow> out, ReconBatch recon,
                               Long accountId, String asset, ReconItemType type,
                               BigDecimal expected, BigDecimal actual) {

        // null 처리 규칙: 한쪽이 null이면 mismatch (데이터 누락)
        if (expected == null && actual == null) return;
        if (expected != null && actual != null && expected.compareTo(actual) == 0) return;

        BigDecimal diff = (expected == null || actual == null) ? null : actual.subtract(expected);

        // severity: 일단 단순 규칙(나중에 정책화 가능)
        ReconSeverity severity = (type == ReconItemType.CLOSING_BALANCE) ? ReconSeverity.HIGH : ReconSeverity.MEDIUM;

        out.add(new ReconDiffRow(
                recon.getId(),
                recon.getBusinessDate(),
                accountId,
                asset,
                type,
                expected,
                actual,
                diff,
                severity,
                "AUTO-COMPARE"
        ));
    }

    private Map<String, ReconRow> index(List<ReconRow> rows) {
        Map<String, ReconRow> m = new HashMap<>();
        for (ReconRow r : rows) {
            String key = r.accountId() + "|" + r.asset().toUpperCase();
            m.put(key, r);
        }
        return m;
    }

    private BigDecimal val(BigDecimal v) { return v; }

    private BigDecimal bd(Long v) {
        return v == null ? null : BigDecimal.valueOf(v);
    }
}
