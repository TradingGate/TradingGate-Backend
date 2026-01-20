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
     * 정산 결과를 (batch_id, account_id, symbol_id) 유니크 기준으로 UPSERT한다.
     *
     * @param batch 정산 배치
     * @param rows 저장할 결과 rows
     * @sideEffect clearing_result 테이블에 insert 또는 update가 수행된다.
     */
    public void upsertResults(ClearingBatch batch, List<ClearingResultRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        ClearingResultStatus status = (batch.getBatchType() == ClearingBatchType.EOD)
                ? ClearingResultStatus.FINAL
                : ClearingResultStatus.PRELIMINARY;

        LocalDate businessDate = batch.getBusinessDate();

        // 왜: 배치 재실행/재시도 시에도 결과를 멱등하게 만들기 위해 UPSERT를 사용한다.
        String sql = """
            insert into clearing_result (
                batch_id, business_date, account_id, symbol_id,
                opening_qty, closing_qty, opening_price, closing_price,
                realized_pnl, unrealized_pnl, fee, funding, status,
                created_at, updated_at
            ) values (
                ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?, ?,
                now(), now()
            )
            on conflict (batch_id, account_id, symbol_id)
            do update set
                business_date = excluded.business_date,
                opening_qty = excluded.opening_qty,
                closing_qty = excluded.closing_qty,
                opening_price = excluded.opening_price,
                closing_price = excluded.closing_price,
                realized_pnl = excluded.realized_pnl,
                unrealized_pnl = excluded.unrealized_pnl,
                fee = excluded.fee,
                funding = excluded.funding,
                status = excluded.status,
                updated_at = now()
            """;

        // 초기 구현에서는 단순성을 위해 고정 배치 사이즈를 사용하고, 추후 성능 테스트 후 프로퍼티로 외부화한다.
        int batchSize = 100;

        jdbcTemplate.batchUpdate(sql, rows, batchSize, (ps, row) -> {
            ps.setLong(1, batch.getId());
            ps.setDate(2, Date.valueOf(businessDate));
            ps.setLong(3, row.accountId());
            ps.setLong(4, row.symbolId());

            ps.setBigDecimal(5, row.openingQty());
            ps.setBigDecimal(6, row.closingQty());
            ps.setBigDecimal(7, row.openingPrice());
            ps.setBigDecimal(8, row.closingPrice());

            ps.setBigDecimal(9, row.realizedPnl());
            ps.setBigDecimal(10, row.unrealizedPnl());
            ps.setBigDecimal(11, row.fee());
            ps.setBigDecimal(12, row.funding());

            ps.setString(13, status.name());
        });
    }
}
