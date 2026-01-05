# Matching Engine (Kafka Worker) -- TradingGate

> ⚠️ **이 문서는 운영 가이드가 아닙니다.**\
> Matching Worker **구성 요소의 설계 의도와 정합성 전략을 기록**하는
> 문서입니다.\
> (실행/운영/설치 문서는 다른 README에서 다룹니다.)

## ❓ 왜 이 파트를 만들었는가

TradingGate의 매칭 엔진은 단순한 주문 처리 모듈이 아니다.\
이 파트의 핵심 질문은 다음과 같다:

> **Kafka 기반 stateful worker 환경에서, 실패 · 재처리 · 리밸런싱이 발생해도 주문 상태와 이벤트 정합성을 어떻게 유지할 것인가?**

Kafka의 기본 처리 모델은 **at-least-once**이다.\
실제 운영에서는 다음 상황이 항상 발생한다:

-   이벤트 발행 실패\
-   워커 프로세스 강제 종료\
-   컨슈머 리밸런싱 (revoke/assign)\
-   중복 메시지 재처리

이 매칭 엔진은 이러한 현실을 전제로,

> **완벽한 exactly-once 대신, "의도된 정합성 보장 범위"를 명확히 정의한 구조로 설계되었다.**

## 🎯 역할과 책임 범위

### 이 파트가 책임지는 것

-   `orders.in` (Kafka) 기반 주문 처리
-   **Partition 단위 상태(OrderBook)** 관리
-   주문 매칭 및 결과 이벤트 발행
-   재시작/리밸런싱 시 **빠른 상태 복구 (Snapshot)**

### 이 파트가 책임지지 않는 것

-   계좌 잔고의 최종 정합성
-   금융 회계 수준의 원자성
-   end-to-end exactly-once 보장

> 이 모듈은 **실시간 처리와 복구 가능성**에 집중하며,\
> 최종 금융 정합성은 API / Risk / Ledger 시스템과의 조합으로 달성된다.

## 🔁 전체 처리 흐름

    orders.in (Kafka)
       ↓
    OrdersInKafkaListener
       ↓
    MatchingWorkerService
       ↓
    MatchingEngine (OrderBook mutation)
       ↓
    MatchingEventPublisher
       ↓
    orders.updated / trades.executed

## 🧭 핵심 처리 원칙

### MANUAL ACK

-   **이벤트 발행이 성공한 경우에만 offset commit**
-   publish 실패 시 ack 하지 않음 → **동일 메시지 재처리**

> Kafka의 재전송 메커니즘을 그대로 활용해 "**처리 완료되지 않은 레코드는 반드시 다시 온다**"를 보장한다.\
> 또한 트랜잭션을 사용하지 않고, 실패 시 재처리를 단순한 기본 경로로 유지한다.

## 🏗️ 설계 포인트

### 1️⃣ 상태는 Partition 단위로 격리

-   OrderBook은 Kafka **partition에 귀속**
-   symbol → partition 매핑은 해시 기반
-   리밸런싱 시 다른 워커가 **동일 partition을 이어받을 수 있음**

### 2️⃣ 실패 시 정합성 전략

#### 이벤트 발행 실패

-   `publishOrderUpdates()` / `publishMatchFills()` 실패 시 예외 발생
-   listener에서 ack 수행 ❌
-   동일 offset 메시지 재처리 ⭕

> 중간 상태가 있더라도, **Kafka가 재시도를 강제**한다.

### 3️⃣  Snapshot은 “정합성 도구”가 아니라 “복구 가속 장치” 이다.

Snapshot 목적:

-   ❌ 정확성 보장
-   ❌ 트랜잭션 대체
-   ✅ **복구 시간 단축**

특징:

-   best-effort 비동기 저장
-   offset 기반 seek
-   실패해도 처리 중단 없음

> Snapshot은 **성능 가속 장치일 뿐**, 정합성은 Kafka 재처리에 의존한다. \
> 또한 Snapshot은 일부 손실되거나 누락되더라도,
> 시스템의 정합성이 깨지지 않도록 설계되었다.

## 🔄 리밸런싱 대응

### Partition Assigned

-   snapshot 존재 → 해당 offset 이후로 seek
-   snapshot 없음 → Kafka 기본 위치 사용
-   epoch 기반 보호로 중복 assign 경쟁 방지

### Partition Revoked (Commit 이후)

-   마지막 offset 기준 snapshot **강제 생성**
-   OrderBook 제거

### Partition Lost

-   snapshot 생성 ❌
-   상태 즉시 폐기
-   Kafka 재처리에 위임

> lost 상황에서는\
> **중복 처리 허용 + 단순성**을 선택.

## 🗂️ Snapshot Lifecycle

    record 처리
       ↓
    SnapshotPolicy 평가
       ↓
    SnapshotWriteQueue enqueue
       ↓
    비동기 파일 저장
       ↓
    Retention 정책 적용

### Shutdown

-   현재 할당된 partition snapshot 강제 생성
-   queue drain 대기
-   best-effort 종료

## 🧪 테스트 전략

이 모듈의 테스트는 “버그 탐지”보다 다음 질문에 답하기 위해 설계되었다:

> **실패와 재처리가 반복되는 환경에서도  
> 최종적으로 올바른 상태와 이벤트에 수렴하는가?**

따라서 단순 커버리지보다는 **정합성 시나리오**에 집중한다.

---

### 1️⃣ 엔진 로직 단위 테스트

Kafka와 분리된 상태에서, 순수 매칭 엔진에 대해 검증한다.

- 가격–시간 우선(Price–Time Priority)
- 부분 체결 / 잔량 처리
- 취소 동작
- 동일 주문의 멱등성(idempotency)

> 도메인 규칙이 흔들리면  
> 그 위의 모든 인프라가 의미가 없기 때문이다.

---

### 2️⃣ Kafka 이벤트 흐름 검증

매칭 결과가 외부 시스템으로 안전하게 전달되는지 확인한다.

- 올바른 토픽/키로 발행되는지
- 재처리 상황에서 **이벤트가 중복되지 않는지**
- 메시지 포맷 변경 시 깨지지 않는지

여기서 중요한 기준은:

> **“메시지는 계약(contract)이다.”**  
> 소비자 입장에서 예측 가능한가?

---

### 3️⃣ 실제 운영 시나리오(E2E)

실제 Kafka( Testcontainers )와 워커를 띄워:

- 실패 후 재처리
- publish 실패 → 재시도
- 리밸런싱 / 재시작
- Snapshot 존재/손상/부재 상황

을 반복적으로 실행한다.

목표는 단순하다.

> **중간에 실패하더라도,  
> 시스템이 결국 일관된 상태로 돌아오느냐.**

---

### 4️⃣ Snapshot 관련 테스트

Snapshot은 “정확성 도구”가 아니므로 이렇게 검증한다.

- 있으면 복구가 빨라진다
- 깨지면 **버리고 다시 읽으면 된다**
- 그럼에도 정합성은 유지된다

즉:

> Snapshot = 최적화  
> Kafka 재처리 = 진짜 안전장치

테스트는 이 관계가 깨지지 않는지를 확인한다.

---

### ✔️ 요약

이 테스트 전략은 다음을 보장한다.

- 같은 입력 → 항상 같은 결과
- 재처리/장애/리밸런싱 이후에도 상태는 **수렴**
- 외부 시스템과 연결된 상태에서도 정합성 유지

> 기능 확장보다 먼저,  
> **“안전하게 망가지고 다시 복구되는 시스템”**을 목표로 한다.

## 🧩 시스템 내 위치 (Context)

이 모듈은 TradingGate 전체 구조에서:

-   API 서버 → 입력 검증
-   **Matching Worker → 실시간 주문 처리**
-   Risk / Ledger → 금융 정합성 보강

> Matching Worker는 **빠른 처리 + 복구 가능성**을 책임지고, 금융 도메인의 최종 원자성은 다른 모듈과 함께 완성된다.\
>  이 모듈은 단독으로 완결된 시스템이 아니라, 상위/하위 컴포넌트와 결합될 때 의미를 갖는 처리 파트이다.

## ✅ 정리

이 매칭 엔진은:

-   실패를 전제로 설계\
-   재처리를 정상 경로로 취급\
-   snapshot을 "가속 장치"로만 사용

Kafka 환경에서 **실제로 유지 가능한 정합성의 경계**를\
명확히 정의한 구현이다.

## 📎 Related Docs

> (추후 확장)

-   SYSTEM_OVERVIEW.md

