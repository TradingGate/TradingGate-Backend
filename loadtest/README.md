# Load Test Guide

이 디렉터리는 TradingGate의 기본 부하/성능 검증 스크립트를 모아둔 곳이다.

## 전제

- 인프라 컨테이너 기동: `docker compose up -d`
- 앱 프로필 기동: `api`, `worker`, `risk`, `clearing`
- 현재 매칭 엔진은 정수 수량 기준으로만 안전하므로 `quantity=1` 기준으로 테스트한다.

## 1. API 부하 테스트 (`k6` via Docker)

### Smoke

```bash
docker run --rm -i \
  -v "$PWD/loadtest/k6:/scripts" \
  grafana/k6 run /scripts/orders-create-smoke.js
```

### Ramp

```bash
docker run --rm -i \
  -v "$PWD/loadtest/k6:/scripts" \
  grafana/k6 run /scripts/orders-create-ramp.js
```

필요 시 환경 변수로 대상 변경 가능:

```bash
docker run --rm -i \
  -e BASE_URL=http://host.docker.internal:8080 \
  -e SYMBOL=BTCUSDT \
  -e PRICE=50000 \
  -e QUANTITY=1 \
  -v "$PWD/loadtest/k6:/scripts" \
  grafana/k6 run /scripts/orders-create-ramp.js
```

## 2. Kafka Burst 테스트

```bash
bash loadtest/scripts/kafka-orders-burst.sh 1000 BTCUSDT 50000 1 10000
```

인자:
- `COUNT`
- `SYMBOL`
- `PRICE`
- `QUANTITY`
- `START_USER_ID`

## 3. Clearing / Recon 배치 시간 측정

```bash
bash loadtest/scripts/batch-benchmark.sh clearing 2026-03-10
bash loadtest/scripts/batch-benchmark.sh recon-linked 2026-03-10
bash loadtest/scripts/batch-benchmark.sh recon-standalone 2026-03-10
```

## 결과 기록 예시

| Test | Load | Duration | Success Rate | p95 | Notes |
|---|---:|---:|---:|---:|---|
| API Smoke | 5 VUs | 1m | 100% | 120ms | 정상 |
| API Ramp | 50 VUs max | 6m | 98.7% | 420ms | 50 VUs부터 지연 증가 |
| Kafka Burst | 1000 orders | burst | n/a | n/a | risk lag 관찰 |
| Clearing EOD | 1 run | n/a | success | n/a | elapsedMs 기록 |
| Recon Linked | 1 run | n/a | success | n/a | diffCount 확인 |

## 현재 측정 결과

| Test | Load | Duration | Success Rate | p95 | Notes |
|---|---:|---:|---:|---:|---|
| API Smoke | 5 VUs | 1m | 100% | 25.69ms | 1395 requests, avg 13.6ms, 23.18 req/s |
| API Ramp | 50 VUs max | 6m | 99.99% | 21.07ms | 78231 requests, avg 11.61ms, 217.25 req/s, status 202 실패 9건 |
| API Soak | 10 VUs | 10m | 99.99% | 15.68ms | 53055 requests, avg 11.04ms, 88.41 req/s, 실패 4건 |
| Kafka Burst | 1000 orders | burst | n/a | n/a | ledger 기준 500 distinct trade_id, 1948 ledger rows, 1948 balance rows |
| Clearing EOD | 1 run | 314ms | success | n/a | `forceNewRun=true` 기준 |
| Recon Linked | 1 run | 191ms | success | n/a | `rerun=true` 기준, `diffCount=0` |
