-- B-5 clearing MVP seed
-- 목적:
-- 1) previous EOD FINAL opening source
-- 2) 당일 거래/수수료 집계
-- 3) carry-over 계정 포함 여부 확인
--
-- 사용 예:
--   - 이 SQL 실행 후, businessDate = 2026-02-23 로 EOD clearing 실행
--   - scope = '' (ALL)

begin;

-- cleanup (필요한 범위만)
delete from clearing_result;
delete from clearing_batch;
delete from ledger_entry;

-- previous EOD final batch (opening source)
insert into clearing_batch (
    business_date, batch_type, run_key, attempt, retry_of_batch_id,
    status, started_at, finished_at, failure_code, remark, snapshot_key,
    cutoff_offsets, scope, created_at, updated_at
) values (
    date '2026-02-22', 'EOD', 'EOD', 1, null,
    'SUCCESS', now(), now(), null, null, 'WM-PREV-EOD',
    '{"max_ledger_id": 0}'::jsonb, '', now(), now()
);

-- account 1001 USDT opening = 100
insert into clearing_result (
    batch_id, business_date, account_id, asset,
    opening_balance, closing_balance, net_change,
    fee_total, trade_count, trade_value,
    status, created_at, updated_at
)
select id, date '2026-02-22', 1001, 'USDT',
       0, 100, 0,
       0, 0, 0,
       'FINAL', now(), now()
from clearing_batch where snapshot_key = 'WM-PREV-EOD';

-- account 2001 USDT opening = 50 (carry-over only)
insert into clearing_result (
    batch_id, business_date, account_id, asset,
    opening_balance, closing_balance, net_change,
    fee_total, trade_count, trade_value,
    status, created_at, updated_at
)
select id, date '2026-02-22', 2001, 'USDT',
       0, 50, 0,
       0, 0, 0,
       'FINAL', now(), now()
from clearing_batch where snapshot_key = 'WM-PREV-EOD';

-- cumulative ledger before target date (so closing snapshot math is consistent)
insert into ledger_entry (account_id, asset, amount, entry_type, trade_id, idempotency_key, created_at) values
(1001, 'USDT', 100.00000000, 'TRADE', 'PREV-1', 'PREV-1:USDT:TRADE', timestamp '2026-02-22 12:00:00'),
(2001, 'USDT',  50.00000000, 'TRADE', 'PREV-2', 'PREV-2:USDT:TRADE', timestamp '2026-02-22 12:01:00');

-- target date (2026-02-23) activity for account 1001
-- periodNetChange = -100 + (-2 fee) + 40 = -62
-- tradeValueGross = abs(-100) + abs(40) = 140
-- tradeCount = 2 (TR-1, TR-2)
insert into ledger_entry (account_id, asset, amount, entry_type, trade_id, idempotency_key, created_at) values
(1001, 'USDT', -100.00000000, 'TRADE', 'TR-1', 'TR-1:USDT:TRADE', timestamp '2026-02-23 10:00:00'),
(1001, 'USDT',   -2.00000000, 'FEE',   'TR-1', 'TR-1:USDT:FEE',   timestamp '2026-02-23 10:00:01'),
(1001, 'USDT',   40.00000000, 'TRADE', 'TR-2', 'TR-2:USDT:TRADE', timestamp '2026-02-23 11:00:00');

-- boundary row: next-day 00:00:00 should be excluded by B-5 date cutoff (< next day)
insert into ledger_entry (account_id, asset, amount, entry_type, trade_id, idempotency_key, created_at) values
(1001, 'USDT', 999.00000000, 'TRADE', 'TR-FUTURE', 'TR-FUTURE:USDT:TRADE', timestamp '2026-02-24 00:00:00');

commit;

-- 기대 결과 요약 (B-5 EOD 2026-02-23 실행 후)
-- account 1001 / USDT : opening=100, net=-62, closing=38, fee=2, tradeCount=2, tradeValue=140
-- account 2001 / USDT : opening=50,  net=0,   closing=50 (carry-over)

