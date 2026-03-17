# Load Test Guide

이 디렉터리는 TradingGate의 주문 intake, 매칭, 정산 배치를 최신 기준으로 재현하는 스크립트를 모아둔 곳이다.

## 전제
- 기준 환경: `local-all` Kubernetes (`tradinggate-demo` namespace)
- 아래 수치는 모두 로컬 단일 노드 개발 환경에서 측정한 값이다.
- 주문 API는 동기 체결 완료가 아니라 Kafka publish 후 `202 Accepted`를 반환하므로, API 수치를 매칭 처리량으로 해석하면 안 된다.
- 매칭 엔진은 현재 `quantity=1` 시나리오로 검증했다.

## 실행 방법

### 1. API intake (`k6`)

Mac / Linux
```bash
docker run --rm -i \
  -e BASE_URL=http://host.docker.internal:18080 \
  -v "$PWD/loadtest/k6:/scripts" \
  grafana/k6 run /scripts/orders-create-smoke.js
```

Ramp / Soak도 동일하게 `orders-create-ramp.js`, `orders-create-soak.js`를 사용하면 된다.

### 2. Kafka burst

```bash
bash loadtest/scripts/kafka-orders-burst.sh 1000 BTCUSDT 50000 1 10000
```

이 스크립트는 HTTP가 아니라 `orders.in`에 직접 주문을 넣어서 downstream side effect를 확인하는 용도다.

### 3. Matching benchmark

```bash
bash loadtest/scripts/matching-benchmark.sh 100 METRICUSDT 50000 1 940000000 metric-burst
```

이 스크립트는 fresh symbol에 주문을 직접 burst한 뒤 worker 로그의 `MATCHING_METRIC`를 집계해서 다음을 출력한다.
- `queueLatencyMs`
- `engineDurationMs`
- `publishDurationMs`
- `totalHandleMs`
- `endToEndMs`

즉 API intake가 아니라, worker가 주문을 소비한 뒤 매칭 계산과 publish에 실제로 얼마가 걸리는지 보는 용도다.

### 4. Clearing / Recon batch benchmark

```bash
bash loadtest/scripts/batch-benchmark.sh clearing 2026-03-11
bash loadtest/scripts/batch-benchmark.sh recon-linked 2026-03-11
```

이 스크립트는 internal settlement API를 호출해 배치 한 번의 wall-clock 시간을 측정한다.

## 최신 검증 결과

기준:
- 환경: `local-all K8s`
- 같은 항목을 여러 번 측정한 경우에는 가장 최신 수치로 갱신했다.
- 아직 다시 측정하지 않은 항목은 마지막으로 검증된 수치를 유지했다.

| Area | Test | Measured At | Load | Latest Result | Interpretation |
|---|---|---|---:|---|---|
| API intake | k6 smoke | `2026-03-17` | 5 VUs, 1m | `1373 requests`, `22.81 req/s`, avg `17.61ms`, p95 `38.71ms`, success `100%` | 주문 접수 API 기준으로는 안정적이다. 다만 매칭 완료 시간은 아니다. |
| API intake | k6 ramp | `2026-03-12` | 50 VUs max, 6m | `74446 requests`, `206.77 req/s`, avg `18.17ms`, p95 `50.48ms`, success `100%` | 로컬/port-forward 환경 기준 intake는 200 req/s 수준까지 안정적으로 동작했다. |
| API intake | k6 soak | `2026-03-12` | 10 VUs, 10m | `53328 requests`, `88.87 req/s`, avg `11.07ms`, p95 `20.63ms`, success `100%` | 장시간 실행에서도 큰 흔들림 없이 유지됐다. |
| Matching pipeline | Kafka burst | `2026-03-12` | `1000 orders` | `500 distinct trade_id`, `1948 ledger rows`, `1948 balance rows` | 주문 이벤트가 실제로 worker, risk, ledger/account_balance까지 흘러가는 것은 확인됐다. |
| Matching engine | matching-benchmark | `2026-03-17` | `100 orders`, fresh symbol | `queue p95 3034ms`, `engine p95 4ms`, `publish p95 35ms`, `worker total p95 36ms`, `end-to-end p95 3039ms` | 이번 환경에서는 매칭 계산보다 Kafka queue 대기 시간이 더 큰 비중이었다. |
| Settlement | Clearing EOD | `2026-03-17` | `ledger_entry 75,000` | `93,965ms`, `SUCCESS`, `resultCount=10,016` | 최신 재검증에서도 대용량 clearing이 끝까지 수행됐다. 현재 배치 비용은 큰 편이다. |
| Settlement | Recon Linked | `2026-03-17` | `account_balance 10,000` | `2,368ms`, `SUCCESS`, `diffCount=0` | 최신 재검증에서도 projection mismatch 없이 완료됐다. |

## 결과 해석

### API intake
- `k6` 수치는 주문 접수 응답시간이다.
- 따라서 `p95 20~50ms`, `88~206 req/s`는 intake 성능으로 해석해야 하고, 매칭 throughput으로 바로 말하면 안 된다.

### Matching
- `matching-benchmark` 기준으로 worker 내부 매칭 계산 자체는 매우 가볍다.
- 최신 측정에서 `engineDurationMs p95 = 4ms`, `publishDurationMs p95 = 35ms`였다.
- 반대로 `queueLatencyMs p95 = 3034ms`가 크게 나온 것은, 체감 지연의 대부분이 worker 앞 Kafka queue 구간에서 발생했다는 뜻이다.
- 따라서 현재 로컬 환경에서는 매칭 로직이 병목이라기보다, 비동기 queue 대기 시간이 더 크게 보였다.

### Settlement
- Clearing과 Recon은 둘 다 정상 완료됐다.
- 최신 재검증 기준으로 대용량 benchmark에서 `Clearing`은 `93.9초`, `Recon`은 `2.37초`로 차이가 컸다.
- 이는 recon이 비교적 가벼운 대조 배치이고, clearing은 원장 집계 + 결과 저장 + outbox 적재까지 포함하는 무거운 배치라는 현재 구조와 맞아떨어진다.

## 결론

- 주문 intake는 로컬 환경 기준 안정적이었다.
- 매칭 엔진 계산 자체는 빠르고, 현재 관측된 지연의 대부분은 queue 대기 시간에 가깝다.
- settlement는 대용량에서도 실행 가능하지만, 현재 비용은 clearing 쪽에 집중되어 있다.
- 따라서 최신 기준 병목 후보는 매칭 로직보다 queue 구간과 clearing batch 쪽으로 보는 것이 더 적절하다.
