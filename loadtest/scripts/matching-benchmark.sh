#!/usr/bin/env bash
set -euo pipefail

COUNT="${1:-100}"
SYMBOL="${2:-METRICUSDT}"
PRICE="${3:-50000}"
QUANTITY="${4:-1}"
START_USER_ID="${5:-940000000}"
CLIENT_PREFIX="${6:-metric-burst}"
NAMESPACE="${NAMESPACE:-tradinggate-demo}"
KAFKA_LABEL="${KAFKA_LABEL:-app=kafka}"
WORKER_LABEL="${WORKER_LABEL:-app=tradinggate-worker}"

if ! [[ "$COUNT" =~ ^[0-9]+$ ]]; then
  echo "COUNT must be numeric" >&2
  exit 1
fi

if ! [[ "$START_USER_ID" =~ ^[0-9]+$ ]]; then
  echo "START_USER_ID must be numeric" >&2
  exit 1
fi

KAFKA_POD="$(kubectl get pod -n "$NAMESPACE" -l "$KAFKA_LABEL" -o jsonpath='{.items[0].metadata.name}')"
WORKER_POD="$(kubectl get pod -n "$NAMESPACE" -l "$WORKER_LABEL" -o jsonpath='{.items[0].metadata.name}')"

if [[ -z "$KAFKA_POD" || -z "$WORKER_POD" ]]; then
  echo "Failed to resolve kafka/worker pod in namespace=$NAMESPACE" >&2
  exit 1
fi

python3 - <<'PY' "$NAMESPACE" "$KAFKA_POD" "$WORKER_POD" "$COUNT" "$SYMBOL" "$PRICE" "$QUANTITY" "$START_USER_ID" "$CLIENT_PREFIX"
import json
import statistics
import subprocess
import sys
import time
from datetime import datetime, timezone


def percentile(sorted_vals, ratio):
    if not sorted_vals:
        return None
    idx = min(len(sorted_vals) - 1, int(len(sorted_vals) * ratio))
    return sorted_vals[idx]


ns, kafka_pod, worker_pod, count, symbol, price, quantity, start_user_id, prefix = sys.argv[1:]
count = int(count)
start_user_id = int(start_user_id)

received_at = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
log_since = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")

messages = []
for i in range(count):
    user = start_user_id + i
    side = "BUY" if i % 2 == 0 else "SELL"
    payload = {
        "commandType": "NEW",
        "userId": user,
        "clientOrderId": f"{prefix}-{user}-{i}",
        "symbol": symbol,
        "side": side,
        "orderType": "LIMIT",
        "timeInForce": "GTC",
        "price": price,
        "quantity": quantity,
        "source": "MATCHING_BENCHMARK",
        "receivedAt": received_at,
    }
    messages.append(json.dumps(payload))

publish_cmd = [
    "kubectl", "exec", "-i", "-n", ns, kafka_pod, "--", "sh", "-lc",
    "cat | rpk topic produce orders.in --brokers localhost:9092 >/dev/null",
]
subprocess.run(publish_cmd, input=("\n".join(messages) + "\n").encode(), check=True)

# Allow worker to consume and flush logs.
time.sleep(5)

log_cmd = ["kubectl", "logs", "-n", ns, worker_pod, "--since-time", log_since]
logs = subprocess.check_output(log_cmd, text=True)
metric_lines = [
    line for line in logs.splitlines()
    if "MATCHING_METRIC" in line and f"symbol={symbol}" in line and prefix in line
]

parsed = []
for line in metric_lines:
    row = {}
    for token in line.split():
        if "=" in token:
            key, value = token.split("=", 1)
            row[key] = value.rstrip(",")
    parsed.append(row)

metric_keys = [
    "queueLatencyMs",
    "engineDurationMs",
    "publishDurationMs",
    "totalHandleMs",
    "endToEndMs",
    "matchFillCount",
    "orderUpdateCount",
]

metrics = {}
for key in metric_keys:
    values = []
    for row in parsed:
        try:
            values.append(int(row[key]))
        except Exception:
            pass
    if values:
        values.sort()
        metrics[key] = {
            "count": len(values),
            "min": values[0],
            "p50": percentile(values, 0.50),
            "p95": percentile(values, 0.95),
            "max": values[-1],
            "avg": round(statistics.fmean(values), 2),
        }

result = {
    "namespace": ns,
    "kafkaPod": kafka_pod,
    "workerPod": worker_pod,
    "symbol": symbol,
    "count": count,
    "price": price,
    "quantity": quantity,
    "startUserId": start_user_id,
    "clientPrefix": prefix,
    "receivedAt": received_at,
    "metricLineCount": len(parsed),
    "metrics": metrics,
}

print(json.dumps(result, indent=2))
PY
