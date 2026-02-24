package org.tradinggate.backend.settlementIntegrity.recon.service.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.settlementIntegrity.recon.domain.ReconBatch;
import org.tradinggate.backend.settlementIntegrity.recon.domain.e.ReconItemType;
import org.tradinggate.backend.settlementIntegrity.recon.domain.e.ReconSeverity;
import org.tradinggate.backend.settlementIntegrity.recon.dto.ReconDiffRow;
import org.tradinggate.backend.settlementIntegrity.recon.dto.ReconRow;

import java.math.BigDecimal;
import java.util.*;

@Component
@RequiredArgsConstructor
public class ReconComparator {

    private final ReconPrecisionPolicy precisionPolicy;

    public List<ReconDiffRow> compare(ReconBatch recon, List<ReconRow> truth, List<ReconRow> snapshot) {
        Map<String, ReconRow> t = index(truth);
        Map<String, ReconRow> s = index(snapshot);

        // 양쪽(account/asset) 키의 합집합으로 비교해 한쪽만 존재하는 데이터도 mismatch로 잡는다.
        Set<String> keys = new HashSet<>();
        keys.addAll(t.keySet());
        keys.addAll(s.keySet());

        List<ReconDiffRow> diffs = new ArrayList<>();

        for (String key : keys) {
            ReconRow tr = t.get(key);
            ReconRow sr = s.get(key);

            Long accountId = (tr != null) ? tr.accountId() : sr.accountId();
            String asset = (tr != null) ? tr.asset() : sr.asset();

            // closing balance만 비교 (ledger 합 vs account_balance.total)
            addIfMismatch(diffs, recon, accountId, asset, ReconItemType.CLOSING_BALANCE,
                    val(tr == null ? null : tr.closingBalance()),
                    val(sr == null ? null : sr.closingBalance()));
        }

        return diffs;
    }

    private void addIfMismatch(List<ReconDiffRow> out, ReconBatch recon,
                               Long accountId, String asset, ReconItemType type,
                               BigDecimal expected, BigDecimal actual) {

        BigDecimal normalizedExpected = precisionPolicy.normalize(asset, expected);
        BigDecimal normalizedActual = precisionPolicy.normalize(asset, actual);

        // 한쪽만 null이면 projection/truth 누락이므로 반드시 diff로 기록한다.
        if (normalizedExpected == null && normalizedActual == null) return;
        if (normalizedExpected != null && normalizedActual != null && normalizedExpected.compareTo(normalizedActual) == 0) return;

        BigDecimal diff = (normalizedExpected == null || normalizedActual == null)
                ? null
                : normalizedActual.subtract(normalizedExpected);

        // v1은 단순 규칙으로 시작하고, 이후 정책 객체로 분리 가능하다.
        ReconSeverity severity = (type == ReconItemType.CLOSING_BALANCE) ? ReconSeverity.HIGH : ReconSeverity.MEDIUM;

        out.add(new ReconDiffRow(
                recon.getId(),
                recon.getBusinessDate(),
                accountId,
                asset,
                type,
                normalizedExpected,
                normalizedActual,
                diff,
                severity,
                "AUTO-COMPARE"
        ));
    }

    private Map<String, ReconRow> index(List<ReconRow> rows) {
        Map<String, ReconRow> m = new HashMap<>();
        for (ReconRow r : rows) {
            String normalizedAsset = r.asset() == null ? null : r.asset().toUpperCase();
            String key = r.accountId() + "|" + normalizedAsset;
            ReconRow existing = m.get(key);
            if (existing == null) {
                m.put(key, normalizedRow(r, normalizedAsset));
                continue;
            }
            // 방어 로직: 중복 row가 들어오면 덮어쓰지 않고 합산 처리한다.
            m.put(key, mergeClosingOnly(existing, r, normalizedAsset));
        }
        return m;
    }

    private BigDecimal val(BigDecimal v) { return v; }

    private ReconRow normalizedRow(ReconRow r, String normalizedAsset) {
        return new ReconRow(
                r.accountId(),
                normalizedAsset,
                r.openingBalance(),
                r.closingBalance(),
                r.netChange(),
                r.feeTotal(),
                r.tradeCount(),
                r.tradeValue()
        );
    }

    private ReconRow mergeClosingOnly(ReconRow a, ReconRow b, String normalizedAsset) {
        BigDecimal mergedClosing = safe(a.closingBalance()).add(safe(b.closingBalance()));
        return new ReconRow(
                a.accountId(),
                normalizedAsset,
                a.openingBalance(),
                mergedClosing,
                a.netChange(),
                a.feeTotal(),
                a.tradeCount(),
                a.tradeValue()
        );
    }

    private BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
