#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-clearing}"
BUSINESS_DATE="${2:-$(date +%F)}"
BASE_URL="${BASE_URL:-http://127.0.0.1:55564}"

case "$MODE" in
  clearing)
    URL="$BASE_URL/internal/settlement/clearing/run?businessDate=$BUSINESS_DATE&batchType=EOD&forceNewRun=true"
    ;;
  recon-linked)
    URL="$BASE_URL/internal/settlement/recon/linked/run?businessDate=$BUSINESS_DATE&rerun=true"
    ;;
  recon-standalone)
    URL="$BASE_URL/internal/settlement/recon/standalone/run?businessDate=$BUSINESS_DATE"
    ;;
  recon-rerun)
    URL="$BASE_URL/internal/settlement/recon/standalone/rerun?businessDate=$BUSINESS_DATE"
    ;;
  *)
    echo "unsupported mode: $MODE" >&2
    echo "supported: clearing | recon-linked | recon-standalone | recon-rerun" >&2
    exit 1
    ;;
esac

START_MS=$(python3 - <<'PY'
import time
print(int(time.time() * 1000))
PY
)

RESPONSE=$(curl -s -X POST "$URL")

END_MS=$(python3 - <<'PY'
import time
print(int(time.time() * 1000))
PY
)

ELAPSED_MS=$((END_MS - START_MS))

echo "[BATCH] mode=$MODE businessDate=$BUSINESS_DATE elapsedMs=$ELAPSED_MS"
echo "$RESPONSE"
