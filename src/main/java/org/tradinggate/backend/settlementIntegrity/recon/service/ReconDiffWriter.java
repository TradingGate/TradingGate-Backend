package org.tradinggate.backend.settlementIntegrity.recon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.settlementIntegrity.recon.domain.ReconBatch;
import org.tradinggate.backend.settlementIntegrity.recon.dto.ReconDiffRow;

import java.sql.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
@Profile("clearing")
public class ReconDiffWriter {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void upsertDiffs(ReconBatch recon, List<ReconDiffRow> diffs) {
        jdbcTemplate.update("delete from recon_diff where recon_batch_id = ?", recon.getId());
        if (diffs == null || diffs.isEmpty()) return;

        String sql = """
            insert into recon_diff (
                recon_batch_id, business_date, account_id, asset, item_type,
                expected_value, actual_value, diff_value,
                severity, status, memo, created_at, updated_at
            ) values (
                ?, ?, ?, ?, ?,
                ?, ?, ?,
                ?, 'OPEN', ?, now(), now()
            )
            on conflict (recon_batch_id, account_id, asset, item_type)
            do update set
                expected_value = excluded.expected_value,
                actual_value = excluded.actual_value,
                diff_value = excluded.diff_value,
                severity = excluded.severity,
                status = 'OPEN',
                memo = excluded.memo,
                updated_at = now()
            """;

        jdbcTemplate.batchUpdate(sql, diffs, 500, (ps, d) -> {
            ps.setLong(1, d.reconBatchId());
            ps.setDate(2, Date.valueOf(d.businessDate()));
            ps.setLong(3, d.accountId());
            ps.setString(4, d.asset().toUpperCase());
            ps.setString(5, d.itemType().name());

            ps.setBigDecimal(6, d.expectedValue());
            ps.setBigDecimal(7, d.actualValue());
            ps.setBigDecimal(8, d.diffValue());

            ps.setString(9, d.severity().name());
            ps.setString(10, d.memo());
        });
    }
}
