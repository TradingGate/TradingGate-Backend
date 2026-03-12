#!/usr/bin/env bash

set -euo pipefail

NAMESPACE="${NAMESPACE:-tradinggate-demo}"
LEDGER_POD="${LEDGER_POD:-$(kubectl get pod -n "$NAMESPACE" -l app=ledger-postgres -o jsonpath='{.items[0].metadata.name}')}"
LEDGER_DB_USER="${LEDGER_DB_USER:-ledger_user}"
LEDGER_DB_NAME="${LEDGER_DB_NAME:-ledger_db}"
BUSINESS_DATE="${1:-2026-03-11}"
ACCOUNT_COUNT="${2:-5000}"
TRADES_PER_ACCOUNT="${3:-5}"
ACCOUNT_BASE="${ACCOUNT_BASE:-900000000}"

if [[ -z "$LEDGER_POD" ]]; then
  echo "[SEED] ledger-postgres pod not found in namespace=$NAMESPACE" >&2
  exit 1
fi

echo "[SEED] namespace=$NAMESPACE pod=$LEDGER_POD businessDate=$BUSINESS_DATE accountCount=$ACCOUNT_COUNT tradesPerAccount=$TRADES_PER_ACCOUNT accountBase=$ACCOUNT_BASE"

kubectl exec -i -n "$NAMESPACE" "$LEDGER_POD" -- \
  psql -U "$LEDGER_DB_USER" -d "$LEDGER_DB_NAME" -v ON_ERROR_STOP=1 \
  -v business_date="'$BUSINESS_DATE'" \
  -v account_count="$ACCOUNT_COUNT" \
  -v trades_per_account="$TRADES_PER_ACCOUNT" \
  -v account_base="$ACCOUNT_BASE" <<'SQL'
BEGIN;

DELETE FROM account_balance
WHERE account_id >= :account_base
  AND account_id < (:account_base + :account_count);

DELETE FROM ledger_entry
WHERE account_id >= :account_base
  AND account_id < (:account_base + :account_count);

WITH accounts AS (
    SELECT (:account_base + gs)::bigint AS account_id
    FROM generate_series(0, :account_count - 1) AS gs
),
trade_rows AS (
    SELECT
        a.account_id,
        t.trade_no,
        ('bulk-' || a.account_id || '-' || t.trade_no) AS trade_id,
        ((a.account_id - :account_base) % 7 + 1)::numeric(20,8) AS qty,
        (1000 + ((a.account_id - :account_base) % 100))::numeric(20,8) AS price,
        ((a.account_id - :account_base) % 3 + 1)::numeric(20,8) AS fee_unit
    FROM accounts a
    CROSS JOIN generate_series(1, :trades_per_account) AS t(trade_no)
)
INSERT INTO ledger_entry (account_id, asset, amount, entry_type, trade_id, idempotency_key, created_at)
SELECT
    tr.account_id,
    payload.asset,
    payload.amount,
    payload.entry_type,
    tr.trade_id,
    tr.trade_id || ':' || tr.account_id || ':' || payload.asset || ':' || payload.entry_type,
    (:business_date::date + time '10:00:00') + make_interval(secs => tr.trade_no)
FROM trade_rows tr
CROSS JOIN LATERAL (
    VALUES
        ('BTC', tr.qty, 'TRADE'),
        ('USDT', -(tr.qty * tr.price), 'TRADE'),
        ('USDT', -(tr.fee_unit / 100), 'FEE')
) AS payload(asset, amount, entry_type);

INSERT INTO account_balance (account_id, asset, available, locked, updated_at)
SELECT
    l.account_id,
    l.asset,
    SUM(l.amount),
    0,
    now()
FROM ledger_entry l
WHERE l.account_id >= :account_base
  AND l.account_id < (:account_base + :account_count)
GROUP BY l.account_id, l.asset;

COMMIT;

SELECT
    COUNT(*) AS ledger_rows
FROM ledger_entry
WHERE account_id >= :account_base
  AND account_id < (:account_base + :account_count);

SELECT
    COUNT(*) AS balance_rows
FROM account_balance
WHERE account_id >= :account_base
  AND account_id < (:account_base + :account_count);
SQL
