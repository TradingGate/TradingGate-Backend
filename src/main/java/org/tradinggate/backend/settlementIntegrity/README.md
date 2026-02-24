# Settlement Integrity (B-5 / B-6)

## 개요

나는 **TradingGate**라는 트레이딩 코어 시스템을 설계하고 구현했다.

이 프로젝트는 단순 매매 기능 구현에 그치지 않고, 금융 시스템에서 가장 중요한 문제인 **정합성(Consistency)** 을 어떻게 유지할 것인가에 초점을 둔다.  
특히 이번 스프린트에서는 **B-5(Clearing)** 와 **B-6(Reconciliation)** 를 대상으로, 이벤트 스트림(Kafka)에 직접 의존하지 않는 **DB 기반 정산/대사 구조**를 설계하고 구현했다.

핵심 목표는 다음 두 가지였다.

- `ledger_entry`를 **Single Source of Truth(SSOT)** 로 유지한다.
- `account_balance`(Projection)를 항상 **검증 가능한 상태**로 운영한다.

즉, 이 설계의 본질은 “잔고를 계산하는 시스템”이 아니라, **돈의 이동을 기록한 원장을 기준으로 계산과 검증을 분리한 시스템**을 만드는 것이다.

---

## 왜 이렇게 설계했는가

### 1. 이벤트 기반 시스템의 현실적 문제

초기 접근은 Kafka 이벤트를 기준으로 정산/대사를 구성하는 방식이었지만, 실무 관점에서는 다음 문제가 반복된다.

- Consumer offset 관리 복잡도
- 리밸런싱/재처리 시점에 따른 기준점 흔들림
- “정산 시점 스냅샷”과 “실시간 유입 이벤트”가 섞이며 생기는 race condition
- 장애 재시도 시 동일 결과를 재현하기 어려움

정산(Clearing)과 대사(Recon)는 “빠르게 처리하는 것”보다 **같은 입력이면 항상 같은 결과를 만드는 것**이 더 중요하다.  
그래서 B-5/B-6는 Kafka를 직접 consume하지 않고, 이미 B-1에서 정규화된 DB 상태를 기준으로 동작하도록 설계를 전환했다.

### 2. SSOT를 중심으로 책임을 다시 나눈 이유

`account_balance`는 사용자 조회/운영에 필요한 빠른 Projection이지만, Projection은 언제든 버그/장애로 틀어질 수 있다.  
반면 `ledger_entry`는 자산 이동의 영구 기록이며, 시스템의 **회계적 진실(Truth)** 이다.

따라서 설계 철학은 명확하다.

- **계산(Clearing)** 은 원장을 기준으로 한다.
- **검증(Recon)** 은 원장과 Projection을 비교한다.
- Projection은 결과물이 아니라 **검증 대상**이다.

이 구조를 통해 “정산 결과”와 “실시간 조회 상태”를 혼동하지 않고, 문제가 발생했을 때 원인 추적이 쉬워진다.

---

## 아키텍처 전제 (B-1 → B-5/B-6)

### B-1의 역할 (이벤트 정규화 계층)

B-1은 `trades.executed` 이벤트를 consume하여 다음을 수행한다.

1. `ledger_entry`에 자산 이동 기록 (원장)
2. `ledger_entry`를 기반으로 `account_balance` 갱신 (Projection)

이 전제를 기준으로 B-5/B-6는 다음처럼 동작한다.

- `ledger_entry` = **SSOT**
- `account_balance` = **조회용 Projection (대사 대상)**
- B-5/B-6 = **DB 기반 배치 처리**, Kafka 직접 consume 없음

이렇게 계층을 분리하면, 이벤트 처리(B-1)의 복잡도와 정산/대사(B-5/B-6)의 정합성 책임이 섞이지 않는다.

---

## B-5 Clearing v1: 계산의 역할

### 역할 정의

B-5는 특정 주기(Intraday/EOD)마다 `(account_id, asset)` 단위로 정산 스냅샷을 만든다.

즉, B-5의 책임은 “현재 Projection을 읽어 적당히 저장”하는 것이 아니라,
**원장 기준으로 특정 배치 시점의 스냅샷을 확정**하는 것이다.

### 핵심 설계 포인트

#### 1. 워터마크(`max_ledger_id`) 기반 스냅샷

정산 배치가 `RUNNING`으로 전이되는 순간, DB에서 `max_ledger_id`를 워터마크로 선점한다.

이 워터마크는 정산의 기준점이며, 이후 집계는 반드시 다음 범위만 읽는다.

- `ledger_entry.id <= max_ledger_id`

이 전략의 장점:

- 배치 도중 들어온 실시간 거래가 정산 결과를 오염시키지 않음
- 재시도 시 동일 워터마크 기준으로 같은 결과 재현 가능
- “이 정산 결과가 어느 시점까지의 원장을 반영했는지” 설명 가능

정산 시스템에서 가장 중요한 속성인 **결정론성(determinism)** 을 확보하는 핵심 전략이다.

#### 2. `closing_balance`는 Projection이 아닌 원장 누적합으로 계산

`closing_balance`를 `account_balance`에서 읽으면 실시간 변경과 섞여 오차가 생길 수 있다.  
그래서 B-5는 `account_balance`를 신뢰하지 않고, 워터마크 이하 원장 누적합으로 계산한다.

이 선택은 성능보다 정확성을 우선한 결정이다.

#### 3. `opening_balance`는 이전 final snapshot 기준

`opening_balance`는 이전 EOD final snapshot의 `closing_balance`를 기준으로 연결한다.  
이렇게 해야 자금 흐름의 연속성이 보장된다.

- 오늘 거래가 없는 계정도 carry-over 대상으로 포함
- 어제 `closing` = 오늘 `opening`

즉, B-5는 “오늘 거래한 계정만 요약하는 배치”가 아니라, **잔고 체인을 유지하는 정산 배치**다.

### B-5 v1 범위 (의도적 단순화)

이번 v1은 `(account_id, asset)` 단위 정산에 집중한다.

- `opening_balance`
- `closing_balance`
- `net_change`
- `fee_total`
- `trade_count`
- `trade_value`

포지션/평가손익/시세 기반 정산은 제외했다.  
이유는 MVP 단계에서 먼저 해결해야 하는 문제가 “평가손익 계산”보다 **금액 정합성 보장**이기 때문이다.

---

## B-6 Reconciliation v1: 검증의 역할

### 역할 정의

B-6는 B-5 결과를 검증하는 모듈이 아니다.  
B-6의 1차 목적은 **B-1이 만든 Projection(account_balance)이 원장과 일치하는지 검증**하는 것이다.

즉, B-6는 다음을 비교한다.

- **Truth (기대값)**: `SUM(ledger_entry.amount)`
- **Snapshot (실제값)**: `account_balance.total`

### Clearing과 Recon의 역할 차이

- **Clearing(B-5)**: 원장을 기준으로 “배치 시점 스냅샷”을 **계산/확정**한다.
- **Recon(B-6)**: 원장과 Projection을 비교해서 “현재 시스템 상태”를 **검증/탐지**한다.

둘 다 원장을 기준으로 하지만, 역할은 다르다.

- Clearing = 계산기
- Recon = 감사기 / 검증기

이 책임 분리를 명확히 해두면, 정산 로직 변경과 대사 로직 변경이 서로를 오염시키지 않는다.

### B-6 v1의 현실적 모델 (best-effort)

B-6는 운영 중 live DB를 조회한다. 따라서 실시간 거래가 유입되는 동안에는 완벽한 동결 스냅샷 비교가 아니다.

이번 v1에서 의도한 모델은 다음과 같다.

- **live DB best-effort eventual consistency model**
- 치명적인 mismatch 탐지 우선
- stale diff 제거 보장 (재실행 시 이전 오탐 잔존 방지)

향후에는 maintenance window / lock 기반 대사로 확장할 수 있지만, MVP에서는 운영 가치를 빠르게 확보하는 데 집중했다.

---

## 설계 철학 (핵심 축)

### 1. Single Source of Truth 유지

금액 정합성 판단의 기준은 항상 `ledger_entry`다.

- 정산도 원장을 기준으로 계산
- 대사도 원장을 기준으로 비교
- Projection(`account_balance`)은 빠른 조회를 위한 캐시이자 검증 대상

이 철학이 흔들리면, 장애 발생 시 “무엇이 기준인지” 설명할 수 없게 된다.

### 2. 계산과 검증의 책임 분리

정산(Clearing)과 대사(Recon)는 둘 다 중요하지만 목적이 다르다.

- 정산은 스냅샷을 만든다.
- 대사는 스냅샷/Projection의 신뢰도를 확인한다.

같은 모듈에 섞으면 구현은 빨라질 수 있어도, 장애 분석과 재처리 정책이 복잡해진다.

### 3. 워터마크 기반 스냅샷 정확성

이번 스프린트에서 가장 중요한 설계 선택 중 하나는 워터마크 전략이다.

- `RUNNING` 전이 시 워터마크 선점
- 워터마크 이하만 집계
- 동일 배치 재실행 시 동일 결과

이 전략 덕분에 “배치가 도는 동안 거래가 계속 발생하는 환경”에서도 정산 기준점을 안정적으로 유지할 수 있다.

### 4. 의도적인 단순화 (MVP)

이번 스프린트는 단일 통화(KRW) 기반 시나리오를 염두에 둔 단순화 철학을 유지했다.  
실제 구현은 자산 문자열(`asset`) 기반으로 일반화되어 있지만, 설계 판단은 “먼저 정합성 코어를 완성한다”는 방향에 맞춰져 있다.

복잡한 포지션/PnL/평가정산보다, 먼저 해결해야 할 것은:

- 돈이 복제되지 않는가?
- 돈이 사라지지 않는가?
- Projection이 틀어지면 탐지 가능한가?

이 질문에 답할 수 있는 구조를 먼저 만들고, 이후 확장하는 전략을 택했다.

---

## 이번 스프린트에서 구현한 것 (요약)

### B-5 Clearing v1

- DB 원장 기반 cumulative snapshot 모델
- 워터마크(`max_ledger_id`) 기반 배치 집계
- `(account_id, asset)` 단위 정산
- `closing_balance` = ledger 누적합 기준 계산
- `opening_balance` = 이전 final snapshot 기준
- carry-over 계정 포함
- 멱등성 보장 (동일 batch 재실행 시 동일 결과)

### B-6 Recon v1

- `ledger_entry` 합 vs `account_balance.total` 직접 비교
- Kafka 직접 consume 없음
- 허용 오차 0 (자산별 precision normalization 후 exact match)
- stale diff 제거 보장
- standalone recon 수동 재실행(new attempt) 경로 제공

---

## 테스트 및 검증 전략 (요약)

이번 파트는 단순 기능 구현으로 끝내지 않고, 다음 레이어로 검증했다.

- **Unit Test**: 계산식, 재시도 정책, precision 정책, 비교 로직
- **Repository Test (PostgreSQL/Testcontainers)**: 워터마크 쿼리, 상태 전이, 집계/조회 경계조건
- **Integration Test (PostgreSQL/Testcontainers)**: `ClearingBatchRunner`, `ReconBatchRunner` 실제 시나리오/분기

또한 스테이징 검증용으로 seed SQL과 runbook도 작성했다.

---

## 마무리

이번 스프린트의 핵심 성과는 B-5/B-6 기능 자체보다,  
**정산과 대사를 원장 중심 구조로 재정의하고, Projection을 검증 가능한 대상으로 격하한 아키텍처 전환**에 있다.

이 구조는 이후 포지션/PnL/평가정산으로 확장하더라도, 가장 중요한 정합성 축(SSOT + 검증 가능성)을 유지하는 기반이 된다.

