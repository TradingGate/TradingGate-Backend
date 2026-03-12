#!/usr/bin/env bash
set -euo pipefail

COUNT="${1:-100}"
SYMBOL="${2:-BTCUSDT}"
PRICE="${3:-50000}"
QUANTITY="${4:-1}"
START_USER_ID="${5:-10000}"
CONTAINER="${KAFKA_CONTAINER:-kafka}"
TOPIC="${KAFKA_TOPIC:-orders.in}"

if ! [[ "$COUNT" =~ ^[0-9]+$ ]]; then
  echo "COUNT must be numeric" >&2
  exit 1
fi

TMP_FILE="$(mktemp)"
TS="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

for ((i=0; i<COUNT; i++)); do
  USER_ID=$((START_USER_ID + i))
  if (( i % 2 == 0 )); then
    SIDE="BUY"
    CLIENT_ID="burst-buy-${USER_ID}-${i}"
  else
    SIDE="SELL"
    CLIENT_ID="burst-sell-${USER_ID}-${i}"
  fi

  printf '%s:{"commandType":"NEW","userId":%d,"clientOrderId":"%s","symbol":"%s","side":"%s","orderType":"LIMIT","timeInForce":"GTC","price":"%s","quantity":"%s","source":"LOADTEST","receivedAt":"%s"}\n' \
    "$SYMBOL" "$USER_ID" "$CLIENT_ID" "$SYMBOL" "$SIDE" "$PRICE" "$QUANTITY" "$TS" >> "$TMP_FILE"
done

echo "[LOADTEST] publishing ${COUNT} orders to ${TOPIC} via ${CONTAINER}"
docker exec -i "$CONTAINER" kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic "$TOPIC" \
  --property parse.key=true \
  --property key.separator=: < "$TMP_FILE"

rm -f "$TMP_FILE"
echo "[LOADTEST] publish complete"
