# Settlement Integrity 모듈 테스트 리포트 (B-5 / B-6)

## 📋 개요

본 리포트는 TradingGate 백엔드의 **Settlement Integrity 영역 (B-5 Clearing / B-6 Reconciliation)** 개발 검증 및 테스트 코드 작성 결과를 정리합니다.

이번 스프린트의 목표는 단순 배치 기능 구현이 아니라, 다음 설계 원칙을 실제 코드와 테스트로 고정하는 것이었습니다.

- `ledger_entry`를 **SSOT (Single Source of Truth)** 로 유지
- `account_balance`를 **Projection(검증 대상)** 으로 취급
- B-5/B-6를 Kafka 직접 consume 없이 **DB 기반**으로 동작
- B-5는 워터마크 기반 스냅샷 정확성/멱등성 보장
- B-6는 원장 vs Projection 직접 대조 + stale diff 제거 보장

**작업 일자**: 2026-02-24  
**검증 범위**: B-5 (Clearing v1), B-6 (Recon v1)

---

## ✅ 첫 번째 작업: 설계 적합성 및 구현 정렬 검증

### 검증 결과: **MVP 요구사항 기준 핵심 항목 반영 완료**

| 영역 | 요구사항 축 | 구현 상태 | 평가 |
| --- | --- | --- | --- |
| **B-5 Clearing** | DB 원장 기반 정산 | ✅ 반영 | `ledger_entry` 기준 집계/스냅샷 |
| **B-5 Watermark** | 배치 시점 기준점 고정 | ✅ 반영 | DB 단일 SQL(CTE) 기반 워터마크 선점 |
| **B-5 Idempotency** | 동일 batch 재실행 결정론성 | ✅ 반영 | 워터마크 + upsert 결과 저장 |
| **B-5 Carry-over** | 무거래 잔고 계정 포함 | ✅ 반영 | 워터마크 이하 원장 합 기준 유니버스 |
| **B-6 Recon** | ledger vs account_balance 직접 비교 | ✅ 반영 | Clearing 결과 검증이 아닌 운영 원장/잔고 비교 |
| **B-6 Diff Idempotency** | stale diff 제거 | ✅ 반영 | 배치 재실행 시 기존 diff 삭제 후 재기록 |
| **B-6 False Positive 방지** | precision normalization | ✅ 반영 | 자산별 scale 정규화 후 exact match |
| **Retry 정책 명시화** | Clearing / Recon 분리 | ✅ 반영 | Clearing=new attempt, Recon=same attempt(+manual new attempt) |

---

## 📊 B-5 Clearing 검증 요약

### ✅ 핵심 설계 반영 사항

#### 1. 워터마크 기반 배치 스냅샷

- `ClearingBatchRepository.tryMarkRunningWithDbWatermark(...)`
- DB에서 `max_ledger_id` 계산 + `RUNNING` 전이를 **원자적으로 처리**
- `cutoff_offsets.max_ledger_id` 저장

**의미**:

- 배치 도중 유입된 거래가 결과를 오염시키지 않음
- 동일 배치 재실행 시 결과 재현 가능
- 정산 결과의 기준점 설명 가능

#### 2. `closing_balance`는 원장 누적합 기반

- `account_balance`를 읽지 않고
- `ledger_entry (id <= max_ledger_id)` 누적합으로 계산

**의미**:

- Projection 오염/실시간 변경과 무관한 정산 스냅샷 확보

#### 3. `opening_balance`는 이전 EOD FINAL 기준 + fallback

- 원칙: 이전 final snapshot (`clearing_result`)의 closing
- fallback: `opening = closing - periodNetChange`

**의미**:

- carry-over 체인 유지
- 초기 데이터 없는 계정도 산술 일관성 유지

#### 4. 유니버스 확장 (carry-over 포함)

- 오늘 거래 발생 계정뿐 아니라
- 워터마크 이하 원장 합 기준 잔고 보유 계정까지 포함

#### 5. Clearing sanity check (fail-fast)

- `opening + netChange == closing`
- `feeTotal >= 0`, `tradeValue >= 0`
- `tradeCount == 0 -> tradeValue == 0`

**의미**:

- 조용한 계산 오류를 배치 단계에서 즉시 감지

---

## 📊 B-6 Recon 검증 요약

### ✅ 핵심 설계 반영 사항

#### 1. 대조 기준: 원장 vs Projection 직접 비교

- Truth: `SUM(ledger_entry.amount)`
- Snapshot: `account_balance.total`

**의미**:

- B-6의 목적을 “Clearing 결과 검증”이 아니라 “Projection 정합성 검증”으로 고정

#### 2. stale diff 제거 보장

- `ReconDiffWriter`에서 재실행 시 기존 `recon_diff` 삭제 후 재기록

**의미**:

- 이전 실행의 오탐/해결된 mismatch가 잔존하지 않음

#### 3. precision normalization 기반 오탐 방지

- 자산별 scale (`KRW`, `USDT`, `BTC`, `ETH`, `USD`) 정규화
- 정규화 후 exact 비교(허용오차 0)

**의미**:

- recon 신뢰도 향상 (소수점 scale 차이로 인한 불필요한 경보 감소)

#### 4. standalone recon 수동 재실행(new attempt)

- `ReconBatchRunner.rerunStandaloneNewAttempt(...)` 추가
- 같은 날짜 재대사를 새 attempt로 생성 후 수행
- 이전 batch/diff는 감사용으로 유지

**의미**:

- 운영자가 projection 수정 후 안전하게 재검증 가능

#### 5. standalone 날짜 분리 안정화

- `clearing_batch_id = 0` 고정 구조에서 날짜가 다른 standalone batch가 섞이지 않도록
- `businessDate` 스코프 조회 + global attempt namespace 전략 적용

**의미**:

- 기존 스키마 제약을 유지하면서 날짜별 배치 재사용 버그 방지

---

## 🧪 두 번째 작업: 테스트 코드 개발 및 확장

이번 스프린트에서는 단순 단위 테스트에 그치지 않고, **Unit / Repository(PostgreSQL) / Integration(PostgreSQL)** 3단계로 검증 계층을 구성했습니다.

### 1) 단위 테스트 (Unit Tests)

#### Clearing

- `DefaultClearingCalculatorTest`
  - fallback opening 계산
  - provided opening 우선 사용
  - zero-noise row skip
  - self-check 실패 (`opening + net != closing`)
  - self-check 실패 (`tradeCount==0 && tradeValue!=0`)

- `ClearingBatchServiceTest`
  - FAILED batch 기준 retry(new attempt) 생성
  - FAILED 아닌 source 배치 거부

#### Recon

- `ReconBatchServiceTest`
  - 기본 재시도 정책(same attempt reuse)
  - next attempt retry 생성
  - standalone 날짜 분리 attempt 할당
  - standalone manual rerun(new attempt) 생성

- `ReconBatchRunnerTest`
  - standalone summary 저장
  - clearing 없음 -> standalone fallback
  - clearing non-success -> skip

- `ReconComparatorTest`
  - precision normalization 후 오탐 없음
  - mismatch 생성
  - duplicate key merge

- `ReconPrecisionPolicyTest`
  - `KRW`, `USDT`, unknown asset scale 검증

---

### 2) Repository 테스트 (PostgreSQL + Testcontainers)

#### Clearing Repository (`ClearingRepositoryPostgresTest`)

검증 항목:

- `tryMarkRunningWithDbWatermark` 성공 (`PENDING -> RUNNING`)
- 재선점 실패 (`already running`)
- ledger 없음 -> `max_ledger_id = 0`
- `findPreviousClosingBalances`:
  - EOD FINAL만 조회
  - asset 대소문자 매칭
  - account/asset/filter/batchType/status 필터
  - 정렬 (`business_date desc`, `id desc`)
  - same-day 제외
- `LedgerEntryRepository.aggregateDailyLedger`:
  - `periodNetChange`, `feeTotal`, `tradeValueGross`, `tradeCount`
  - 날짜 경계 / watermark 경계
- `sumAllHoldingsUpTo`, `sumByAccountIdAndAssetUpToId`:
  - 워터마크 기준 누적합 검증

#### Recon Repository (`ReconBatchRepositoryPostgresTest`)

검증 항목:

- `markSuccessWithSummary` 정상 업데이트
- `tryMarkRunning` 재전이 실패
- 없는 batch 업데이트 시 `0` 반환
- `markFailed` 상태/사유/remark 업데이트
- `findTopByClearingBatchIdOrderByAttemptDesc`
- `findByClearingBatchIdAndAttempt`
- `findStatusById`
- `findTopByClearingBatchIdAndBusinessDateOrderByAttemptDesc` (standalone 날짜 분리)

**인프라 안정화 작업도 포함**:

- Testcontainers Postgres driver 강제 지정
- Spring Batch SQL init 비활성화
- Redisson auto-configuration 제외
- 테스트 컨텍스트를 JPA 중심으로 축소

---

### 3) 통합 테스트 (PostgreSQL + Testcontainers)

#### `ClearingBatchRunnerIntegrationTest`

검증 시나리오:

1. EOD clearing 성공 경로
- ledger 기반 결과 생성
- carry-over 계정 포함
- watermark/snapshotKey 저장
- outbox 호출

2. self-check 실패 경로
- `opening + net != closing` 오류
- batch `FAILED` 처리
- 결과 미저장

3. policy `SKIP` 경로
- batch `PENDING` 유지
- 결과/이벤트 미생성

4. previous EOD 없음 fallback opening
- `opening = closing - periodNetChange` 검증

5. invalid scope parse 실패
- batch `FAILED` 처리 검증

#### `ReconBatchRunnerIntegrationTest`

검증 시나리오:

1. standalone recon mismatch 탐지 + summary 저장
2. standalone 재실행(skip semantics, 이미 SUCCESS인 배치 재획득 안 함)
3. standalone manual rerun(new attempt) 경로
4. precision normalization 오탐 방지
5. 날짜별 standalone batch 분리 생성
6. `runMostRecentSuccessClearing` fallback -> standalone
7. `runForClearingBatch` non-success clearing -> skip

---

## 🚦 런타임 검증 (실 프로필 구동)

이번에는 테스트 코드 외에 실제 프로필을 함께 띄워 런타임 흐름도 확인했다.

- 기동 프로필: `api / worker / risk / clearing`
- 검증 흐름:
  - `orders.in` 발행
  - `worker` 매칭 및 `trades.executed` 발행
  - `risk`가 `ledger_entry`, `account_balance` 반영
  - 내부 엔드포인트로 `clearing`, `recon` 수동 실행

### 확인 결과

- `risk` 멱등성 키 수정 후 maker/taker 양 계정이 모두 원장에 기록됨
- `clearing` 수동 실행 성공
  - `clearing_batch`: `SUCCESS`
  - `clearing_result`: 4건 생성
- `recon linked` 수동 실행 성공
  - `recon_batch`: `SUCCESS`
  - `recon_diff`: 0건

### 추가한 검증 보강

- `TradeExecutedConsumerTest`
  - worker envelope 형식의 `trades.executed` 파싱 검증
- `RiskModuleIntegrationTest`
  - 같은 `tradeId`라도 서로 다른 계정(maker/taker)은 각각 반영되는지 검증

---

## 🐞 테스트로 발견 및 수정한 이슈

### 1. Clearing invalid scope 예외 처리 누락

**문제**

- `scopeParser.parse(...)` 예외가 `try/catch` 바깥에서 발생
- batch가 `RUNNING`으로 남을 수 있음

**조치**

- `ClearingBatchRunner`에서 `scope parse + context 생성`을 `try` 블록 내부로 이동
- invalid scope도 `FAILED` 처리되도록 수정

**의미**

- 운영자가 잘못된 scope로 수동 실행해도 배치 상태가 일관되게 종료됨

### 2. Risk 체결 멱등성 키가 account 단위를 구분하지 못함

**문제**

- `tradeId + asset + entryType` 기준 멱등성으로 인해
- 같은 체결의 maker/taker 이벤트 중 한쪽 계정만 원장에 반영될 수 있었음

**조치**

- 멱등성 키를 `tradeId + accountId + asset + entryType`으로 변경

**의미**

- 체결 참여자별 원장 기록이 분리되어 `clearing/recon` 입력 데이터가 정상화됨

### 3. Risk가 worker의 `trades.executed` envelope 형식을 직접 처리하지 못함

**문제**

- `TradeExecutedConsumer`가 flat DTO만 기대하고 있어 런타임에서 validation error가 발생했음

**조치**

- nested `body` envelope를 파싱하도록 consumer 로직 보강

**의미**

- 실제 `worker -> risk` 경로를 테스트가 아닌 런타임에서도 그대로 검증 가능해짐

### 4. `CLEARING.SETTLEMENT` outbox가 런타임에서 발행되지 않음

**문제**

- `outbox.topics`에 `CLEARING.SETTLEMENT` 매핑이 없었고,
- `OutboxProperties`가 `Map<String, ...>`로 선언되어 있어 enum key(`CLEARING`) 바인딩 후 조회도 맞지 않았음

**조치**

- `application.yml`에 `outbox.topics.CLEARING."CLEARING.SETTLEMENT" = clearing.settlement` 추가
- `OutboxProperties`의 토픽 맵을 `Map<OutboxProducerType, Map<String, String>>`로 수정

**의미**

- `clearing_result -> outbox_event -> Kafka(clearing.settlement)` 경로가 실제 런타임에서 복구됨
- 검증 시점 기준 `outbox_event`는 `SENT=1954`까지 정상 반영됨

---

## 📈 테스트 커버리지 관점 (정성 평가)

이번 영역은 전역 커버리지 숫자보다, **정합성 리스크가 큰 경로를 직접 검증하는 것**에 초점을 두었습니다.

### 커버된 핵심 위험

- 워터마크 기준점 오염
- 정산 산술 불일치
- carry-over 누락
- recon 오탐(precision)
- recon stale diff 잔존
- standalone 재실행/날짜 분리 혼선
- 정책 분기(SKIP / skip semantics)
- 실제 프로필 간 메시지 포맷 불일치
- 같은 체결의 양 계정 반영 누락

즉, 단순 라인 커버리지보다 **실무에서 사고로 이어질 수 있는 경계 조건**을 우선 커버했습니다.

---

## ▶ 실행한 대표 테스트 명령 (예시)

### Repository 테스트 (PostgreSQL/Testcontainers)

```bash
./gradlew test \
  --tests org.tradinggate.backend.clearing.repository.ClearingRepositoryPostgresTest \
  --tests org.tradinggate.backend.recon.repository.ReconBatchRepositoryPostgresTest
```

### 통합 테스트 (PostgreSQL/Testcontainers)

```bash
./gradlew test \
  --tests org.tradinggate.backend.clearing.service.ClearingBatchRunnerIntegrationTest \
  --tests org.tradinggate.backend.recon.service.ReconBatchRunnerIntegrationTest
```

### Recon 변경 영향 테스트 (standalone rerun/new attempt)

```bash
./gradlew test \
  --tests org.tradinggate.backend.recon.service.ReconBatchServiceTest \
  --tests org.tradinggate.backend.recon.repository.ReconBatchRepositoryPostgresTest \
  --tests org.tradinggate.backend.recon.service.ReconBatchRunnerIntegrationTest
```

### 부하 테스트 (k6 / 운영 런타임)

- API Smoke: `5 VUs / 1m`, 성공률 `100%`, `p95 25.69ms`
- API Ramp: `최대 50 VUs / 6m`, 성공률 `99.99%`, `p95 21.07ms`, `217.25 req/s`
- API Soak: `10 VUs / 10m`, 성공률 `99.99%`, `p95 15.68ms`, `88.41 req/s`
- Kafka Burst: `1000 orders`, ledger 기준 `500 distinct trade_id`, `1948 ledger rows`
- Clearing / Recon 수동 벤치마크: `314ms / 191ms`

### 장애 / 복구 테스트 (Kafka)

- Kafka 중단 시:
  - `api` producer, `worker/risk` consumer 모두 `localhost:9092` 연결 실패 로그 확인
  - `api` 주문 요청은 즉시 실패하지 않고 약 `67초` 대기 후 `202`로 복구됨
- API producer fail-fast 설정 적용 후 재검증:
  - Kafka 중단 시 주문 요청은 `503 / 3.48s`로 빠르게 실패
  - Kafka 복구 후 신규 주문 요청은 `202 / 0.41s`로 정상 회복
- Kafka 재기동 후:
  - `worker`, `risk`가 coordinator 재발견
  - 신규 주문 요청은 `202 / 116ms`로 정상 회복

---

## 🎯 결론

이번 스프린트의 B-5/B-6는 단순 배치 기능 구현을 넘어, 다음 구조를 **코드 + 테스트**로 고정했다는 점에 의미가 있습니다.

- `ledger_entry` 중심의 SSOT 구조
- Projection(`account_balance`)을 검증 대상화한 운영 모델
- Clearing(계산) / Recon(검증) 책임 분리
- 워터마크 기반 정산 스냅샷 정확성
- DB 기반 정산/대사로의 전환과 재현 가능성 확보

MVP 범위에서 필요한 정합성 코어는 충분히 확보되었으며, 이후 확장은 이 기반 위에서 진행할 수 있습니다.
