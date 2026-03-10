-- B-6 recon MVP seed (mismatch case)
-- 목적:
--   ledger_entry (truth) vs account_balance (snapshot) mismatch 탐지 확인
-- 사용 예:
--   - 이 SQL 실행 후, standalone recon 실행 (businessDate 예: 2026-02-23)

begin;

-- cleanup (recon + balance/ledger)
delete from recon_diff;
delete from recon_batch;
delete from account_balance;
delete from ledger_entry;

-- Truth: ledger_entry 합계
insert into ledger_entry (account_id, asset, amount, entry_type, trade_id, idempotency_key, created_at) values
(1001, 'USDT', 100.00000000, 'TRADE', 'RCN-TR-1', 'RCN-TR-1:USDT:TRADE', timestamp '2026-02-23 10:00:00'),
(1002, 'KRW', 5000.00000000, 'TRADE', 'RCN-TR-2', 'RCN-TR-2:KRW:TRADE', timestamp '2026-02-23 10:01:00');

-- Snapshot: account_balance total (available + locked)
-- 1001 USDT intentionally mismatched by -5
insert into account_balance (account_id, asset, available, locked, updated_at) values
(1001, 'usdt', 95.00000000, 0.00000000, now()),
(1002, 'KRW', 5000.00000000, 0.00000000, now());

commit;

-- 기대 결과 (standalone recon 실행 후)
-- recon_batch:
--   diff_count = 1
--   high_severity_count = 1
--   total_abs_diff = 5.00000000
-- recon_diff:
--   (1001, USDT, CLOSING_BALANCE) 1건 생성

