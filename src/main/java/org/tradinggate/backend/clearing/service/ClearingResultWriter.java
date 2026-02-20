package org.tradinggate.backend.clearing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.clearing.domain.ClearingBatch;
import org.tradinggate.backend.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.clearing.domain.e.ClearingResultStatus;
import org.tradinggate.backend.clearing.dto.ClearingResultRow;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Profile("clearing")
public class ClearingResultWriter {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 정산(스냅샷) 결과를 (batch_id, account_id, asset) 유니크 기준으로 UPSERT한다.
     */
    public void upsertResults(ClearingBatch batch, List<ClearingResultRow> rows) {
        if (rows == null || rows.isEmpty()) return;

        ClearingResultStatus status = (batch.getBatchType() == ClearingBatchType.EOD)
                ? ClearingResultStatus.FINAL
                : ClearingResultStatus.PRELIMINARY;

        LocalDate businessDate = batch.getBusinessDate();

        String sql = """
            insert into clearing_result (
                batch_id, business_date, account_id, asset,
                opening_balance, closing_balance, net_change,
                fee_total, trade_count, trade_value,
                status, created_at, updated_at
            ) values (
                ?, ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?,
                ?, now(), now()
            )
            on conflict (batch_id, account_id, asset)
            do update set
                business_date = excluded.business_date,
                opening_balance = excluded.opening_balance,
                closing_balance = excluded.closing_balance,
                net_change = excluded.net_change,
                fee_total = excluded.fee_total,
                trade_count = excluded.trade_count,
                trade_value = excluded.trade_value,
                status = excluded.status,
                updated_at = now()
            """;

        int batchSize = 200;

        jdbcTemplate.batchUpdate(sql, rows, batchSize, (ps, row) -> {
            ps.setLong(1, batch.getId());
            ps.setDate(2, Date.valueOf(businessDate));
            ps.setLong(3, row.accountId());
            ps.setString(4, row.asset());

            ps.setBigDecimal(5, row.openingBalance());   // null 허용
            ps.setBigDecimal(6, row.closingBalance());
            ps.setBigDecimal(7, row.netChange());

            ps.setBigDecimal(8, row.feeTotal());
            ps.setLong(9, row.tradeCount());
            ps.setBigDecimal(10, row.tradeValue());

            ps.setString(11, status.name());
        });
    }
}
