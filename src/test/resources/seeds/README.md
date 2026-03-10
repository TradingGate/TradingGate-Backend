# B-5 / B-6 Seed SQL (MVP)

이 폴더는 `clearing(B-5)` / `recon(B-6)`를 로컬/스테이징에서 빠르게 검증하기 위한 샘플 데이터 SQL입니다.

## 사용 전 주의

- 대상 DB는 PostgreSQL 기준입니다.
- 애플리케이션 스키마가 이미 생성되어 있어야 합니다.
- `recon_batch` 요약 컬럼(`diff_count`, `high_severity_count`, `total_abs_diff`)이 추가된 스키마여야 합니다.

## 파일 설명

- `b5_clearing_case.sql`
  - 전일 EOD FINAL + 누적 ledger + 당일 trade/fee + carry-over 계정
  - B-5 실행 후 `clearing_result` 생성 검증용

- `b6_recon_mismatch_case.sql`
  - `ledger_entry` vs `account_balance` mismatch 데이터
  - B-6 standalone 실행 후 `recon_diff` / `recon_batch summary` 검증용

- `b6_recon_precision_case.sql`
  - precision normalization(USDT 8자리, KRW 0자리) 오탐 방지 케이스
  - B-6 standalone 실행 후 diff가 없어야 하는 케이스

## 권장 검증 순서

1. SQL 실행
2. 해당 배치/러너 실행
3. 결과 조회

예시 조회:

```sql
select * from clearing_batch order by id desc;
select * from clearing_result order by batch_id desc, account_id, asset;

select * from recon_batch order by id desc;
select * from recon_diff order by recon_batch_id desc, account_id, asset;
```

