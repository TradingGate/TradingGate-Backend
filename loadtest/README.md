# Load Test Guide

이 디렉터리는 TradingGate의 기본 부하/성능 검증 스크립트를 모아둔 곳이다.

## 전제

- 인프라 컨테이너 기동: `docker compose up -d`
- 앱 프로필 기동: `api`, `worker`, `risk`, `clearing`
- 현재 매칭 엔진은 정수 수량 기준으로만 안전하므로 `quantity=1` 기준으로 테스트한다.
- 아래 수치는 모두 로컬 단일 노드 개발 환경(Docker / Local Kubernetes)에서 측정한 값이다.
- 주문 API는 동기 체결 완료가 아니라 Kafka에 publish 후 `202 Accepted`를 반환하는 경로이므로, 운영 처리량의 절대 기준값으로 해석하면 안 된다.

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

주의:
- 이 테스트는 HTTP API 성능이 아니라 `orders.in -> worker -> risk -> ledger/account_balance` 파이프라인의 side effect를 관찰하기 위한 용도다.
- 초기 잔고를 충분히 seed하지 않으면 일부 계정에서 음수 잔고/차단 이벤트가 발생할 수 있으므로, 결과는 순수 throughput benchmark가 아니라 현재 시나리오 기준 관찰값으로 본다.

## 3. Clearing / Recon 배치 시간 측정

```bash
bash loadtest/scripts/batch-benchmark.sh clearing 2026-03-10
bash loadtest/scripts/batch-benchmark.sh recon-linked 2026-03-10
bash loadtest/scripts/batch-benchmark.sh recon-standalone 2026-03-10
```

주의:
- 이 스크립트는 배치 실행 시간을 간단히 측정하는 용도다.
- `clearing` / `recon` 수치는 입력 ledger row 수와 결과 row 수에 따라 크게 달라지므로, 단일 숫자만으로 일반 성능을 주장하면 안 된다.

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
| API Ramp | 50 VUs max | 6m | 99.99% | 21.07ms | 78231 requests, avg 11.61ms, 217.25 req/s, `202` 아닌 응답 9건 |
| API Soak | 10 VUs | 10m | 99.99% | 15.68ms | `DURATION=10m` override 기준, 53055 requests, avg 11.04ms, 88.41 req/s, 실패 4건 |
| Kafka Burst | 1000 orders | burst | n/a | n/a | 관찰 결과 기준 500 distinct trade_id, 1948 ledger rows, 1948 balance rows |
| Clearing EOD | 1 run | 314ms | success | n/a | 소규모 데이터셋 기준, `forceNewRun=true` |
| Recon Linked | 1 run | 191ms | success | n/a | 소규모 데이터셋 기준, `rerun=true`, `diffCount=0` |

## 해석 가이드

- `API Smoke / Ramp / Soak`
  - 로컬 비동기 주문 접수 API 기준으로는 무리 없는 수치다.
  - `p95 10~30ms`, `수십~수백 req/s`는 현재 엔드포인트 특성상 자연스러운 범위다.
  - 다만 이는 단일 노드 개발 환경 측정값이며, 운영 capacity claim으로 사용하면 안 된다.
- `Kafka Burst`
  - 체결 수와 ledger side effect를 확인하는 테스트다.
  - `1000 orders -> 500 trades`는 BUY/SELL 쌍 매칭 관점에서 자연스럽지만, `1948 rows`는 현재 시나리오 기준 관찰값으로 보는 것이 맞다.
- `Clearing / Recon`
  - 현재 README의 숫자는 배치 실행이 가능한지 보는 1차 지표다.
  - 일반 성능 비교 자료로 쓰려면 입력 ledger row 수, 결과 row 수, batch 범위를 같이 기록해야 한다.

## 재검증 결과 (2026-03-12, local-all K8s + port-forward)

이번 재검증은 `local-all` Kubernetes 데모 환경에서 `kubectl port-forward`를 통해 수행했다.
이전 표의 수치와 실행 경로가 다르므로, 절대값을 직접 비교하기보다 "같은 시스템이 현재도 안정적으로 동작하는지"를 확인하는 용도로 보는 것이 맞다.

| Test | Load | Duration | Success Rate | p95 | Notes |
|---|---:|---:|---:|---:|---|
| API Smoke | 5 VUs | 1m | 100% | 33.15ms | 1382 requests, avg 15.76ms, 23.03 req/s |
| API Ramp | 50 VUs max | 6m | 100% | 50.48ms | 74446 requests, avg 18.17ms, 206.77 req/s |
| API Soak | 10 VUs | 10m | 100% | 20.63ms | `DURATION=10m`, 53328 requests, avg 11.07ms, 88.87 req/s |
| Clearing EOD | 1 run | 539ms | success | n/a | `resultCount=16`, `forceNewRun=true` |
| Recon Linked | 1 run | 283ms | success | n/a | `diffCount=0`, `rerun=true` |

추가 해석:
- `Smoke`, `Soak`는 기존 README 수치와 큰 차이가 없었다.
- `Ramp`는 기존 측정보다 p95가 높아졌는데, 이번 측정은 `kubectl port-forward`를 경유하므로 동일 조건 비교로 보면 안 된다.
- `Clearing / Recon`은 이번 재검증에서 결과 row 수와 함께 다시 기록했다. 이전 수치보다 시간이 늘었지만 입력 규모도 더 컸다.

## 대용량 배치 benchmark (2026-03-12)

입력 데이터셋:
- 환경: `local-all K8s`
- business date: `2026-03-11`
- seed: `5000 accounts x 5 trades/account`
- 생성 데이터:
  - `ledger_entry = 75,000 rows`
  - `account_balance = 10,000 rows`
  - 기대 `clearing_result ≈ 10,016 rows` (seed 10,000 + 기존 소규모 16)

측정 결과:

| Test | Input | Duration | Result |
|---|---:|---:|---|
| Clearing EOD | `ledger_entry 75,000` | `82,767ms` | `SUCCESS`, `resultCount=10,016` |
| Recon Linked | `account_balance 10,000` | `190ms` | `SUCCESS`, `diffCount=0`, `clearingBatchId=32` |

해석:
- 이 수치는 "대용량에서도 동작하는가"를 보기 위한 1차 benchmark다.
- `Clearing` 시간에는 원장 집계뿐 아니라 `clearing_result` 저장과 `CLEARING.SETTLEMENT` outbox 적재까지 포함된다.
- 대용량 재검증 과정에서 `ClearingOutboxService`의 페이지 조회가 정렬 없이 수행되어 일부 outbox가 누락되는 버그를 발견했고, `id ASC` 정렬로 수정 후 동일 데이터셋에서 `SUCCESS`를 확인했다.
- 현재 `82.7초`는 정합성과 배치 안정성 중심의 MVP 수치로 해석하는 것이 맞고, 대규모 운영 기준 성능 최적화는 후속 과제다.
