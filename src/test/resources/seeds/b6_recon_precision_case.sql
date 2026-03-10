-- B-6 recon MVP seed (precision/no-false-positive case)
-- 목적:
--   ReconPrecisionPolicy (asset scale normalize) 적용 시 오탐이 없는지 확인
--
-- 정책 예시:
--   KRW  -> scale 0
--   USDT -> scale 8

begin;

delete from recon_diff;
delete from recon_batch;
delete from account_balance;
delete from ledger_entry;

-- USDT: 9자리 차이지만 8자리 반올림 후 동일 -> mismatch 없어야 함
insert into ledger_entry (account_id, asset, amount, entry_type, trade_id, idempotency_key, created_at) values
(2001, 'USDT', 10.123456784, 'TRADE', 'P-TR-1', 'P-TR-1:USDT:TRADE', timestamp '2026-02-23 09:00:00');

insert into account_balance (account_id, asset, available, locked, updated_at) values
(2001, 'usdt', 10.123456783, 0.00000000, now());

-- KRW: 소수점 있어도 scale 0 반올림 후 동일
insert into ledger_entry (account_id, asset, amount, entry_type, trade_id, idempotency_key, created_at) values
(2002, 'KRW', 100.4, 'TRADE', 'P-TR-2', 'P-TR-2:KRW:TRADE', timestamp '2026-02-23 09:05:00');

insert into account_balance (account_id, asset, available, locked, updated_at) values
(2002, 'KRW', 100, 0, now());

commit;

-- 기대 결과 (standalone recon 실행 후)
-- recon_batch.diff_count = 0
-- recon_diff = 0건

