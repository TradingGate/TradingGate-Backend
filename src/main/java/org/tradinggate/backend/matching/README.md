# Matching Engine (Kafka Worker) Design Document

> ⚠️ **이 문서는 운영 가이드가 아닙니다.**
>
> Matching Worker 컴포넌트의 **설계 의도와 데이터 정합성(Consistency) 전략**을 기록한 기술 문서입니다.
> (실행, 운영, 설치 가이드는 별도 문서를 참고하십시오.)

## ❓ 설계 배경

TradingGate의 매칭 엔진은 단순한 주문 처리 모듈이 아닙니다. 이 파트 설계의 핵심 질문은 다음과 같습니다.

> **Kafka 기반의 Stateful Worker 환경에서, 장애·재처리·리밸런싱이 발생하더라도 주문 상태와 이벤트의 정합성을 어떻게 유지할 것인가?**

Kafka의 기본 모델은 **at-least-once**이며, 실제 운영 환경에서는 다음 상황이 필연적으로 발생합니다.
* 이벤트 발행(Publish) 실패
* 워커 프로세스 강제 종료
* 컨슈머 리밸런싱 (Revoke/Assign)
* 중복 메시지 수신 및 재처리

따라서 본 엔진은 완벽한 Exactly-Once를 지향하기보다, **"의도된 정합성 보장 범위"**를 명확히 정의하고 현실적인 복구 가능성에 초점을 맞췄습니다.

## 🎯 역할과 책임 (Roles & Responsibilities)

매칭 엔진이 담당하는 영역과 그렇지 않은 영역은 다음과 같이 명확히 구분됩니다.

| 구분 | 책임 범위 (In Scope) | 책임 제외 (Out of Scope) |
| :--- | :--- | :--- |
| **핵심 기능** | `orders.in` (Kafka) 토픽 기반 주문 처리 | 계좌 잔고의 최종 정합성 |
| **상태 관리** | **Partition 단위**의 OrderBook 상태 관리 | 금융 회계 수준의 원자성(Atomicity) |
| **이벤트** | 주문 체결(Match) 및 결과 이벤트 발행 | End-to-End Exactly-Once 보장 |
| **복구** | 리밸런싱/재시작 시 **빠른 상태 복구 (Snapshot)** | - |

> **Note**: 이 모듈은 **실시간 처리 성능과 장애 복구**에 집중합니다. 최종적인 금융 무결성은 API, Risk, Ledger 시스템과의 유기적 결합을 통해 달성됩니다.

## 🔁 처리 흐름 (Processing Flow)

```mermaid
graph TD
    A[orders.in (Kafka)] -->|Consume| B(OrdersInKafkaListener)
    B -->|Delegate| C{MatchingWorkerService}
    C -->|Mutate| D[MatchingEngine\n(OrderBook State)]
    D -->|Result| E[MatchingEventPublisher]
    E -->|Publish| F[orders.updated / trades.executed]
🧭 핵심 처리 원칙
MANUAL ACK 전략
이벤트 발행이 성공한 경우에만 Offset Commit을 수행합니다.

Publish 실패 시 ACK를 하지 않음으로써, Kafka가 메시지를 재전송하도록 유도합니다.

Kafka의 재전송 메커니즘을 그대로 활용하여 "처리 완료되지 않은 레코드는 반드시 다시 유입된다"는 사실을 보장합니다. 복잡한 트랜잭션 관리 대신, 실패 시 재처리를 단순하고 견고한 기본 경로로 택했습니다.

🏗️ 상세 설계
1️⃣ Partition 단위 상태 격리
OrderBook은 Kafka Partition에 1:1로 귀속됩니다.

Symbol과 Partition의 매핑은 해시(Hash)를 따릅니다.

리밸런싱 발생 시, 다른 워커가 동일 Partition(과 OrderBook)을 이어받아 처리를 지속합니다.

2️⃣ 장애 대응 및 정합성 전략
이벤트 발행 실패 시:

publishOrderUpdates() 또는 publishMatchFills() 실패 시 예외를 발생시킵니다.

Listener는 ACK를 수행하지 않습니다.

결과적으로 동일 Offset의 메시지가 재처리됩니다.

중간 상태가 일부 메모리에 남더라도, Kafka의 강제 재시도를 통해 상태를 덮어쓰거나 올바른 결과로 수렴시킵니다.

3️⃣ Snapshot의 역할
Snapshot은 "정합성 보장 도구"가 아닌 **"복구 가속 장치(Accelerator)"**입니다.

목적: 장애 복구 시간(RTO) 단축

특징:

Best-effort 방식의 비동기 저장

복구 시 Offset 기반 Seek 지원

저장 실패가 메인 처리 로직을 중단시키지 않음

Snapshot은 성능 가속 장치일 뿐, 데이터 정합성은 전적으로 Kafka 재처리에 의존합니다. Snapshot 파일이 유실되더라도 시스템의 무결성은 깨지지 않도록 설계되었습니다.

🔄 리밸런싱 대응
Partition Assigned (할당)
Snapshot 있음: 해당 Offset 이후로 Seek하여 빠르게 상태 복원

Snapshot 없음: Kafka의 기본 Offset 전략(Earliest/Latest) 사용

안전장치: Epoch 검사를 통해 중복 할당 경쟁(Race Condition) 방지

Partition Revoked (회수)
Commit이 완료된 시점 기준으로 마지막 Snapshot 강제 생성 시도

메모리 내 OrderBook 상태 제거

Partition Lost (유실)
Snapshot 생성 없이 즉시 상태 폐기

이후 Kafka 재처리 메커니즘에 위임

Partition Lost 상황에서는 데이터 정확성보다 중복 처리를 허용하더라도 시스템을 멈추지 않는 단순성을 선택했습니다.

🗂️ Snapshot Lifecycle

Record 처리 → SnapshotPolicy 평가 → Queue Enqueue → 비동기 파일 저장 → Retention 정리

Shutdown
현재 할당된 Partition에 대해 Snapshot 강제 생성 시도

Queue Drain (잔여 작업 처리) 대기

제한 시간 내 미완료 시 Best-effort로 종료

🧪 테스트 전략
이 모듈의 테스트는 단순한 버그 탐지를 넘어 다음 핵심 질문을 검증합니다.

"실패와 재처리가 반복되는 환경에서도, 최종적으로 시스템은 올바른 상태와 이벤트에 수렴하는가?"

1. 엔진 로직 단위 테스트
Kafka와 분리된 순수 비즈니스 로직을 검증합니다.

가격-시간 우선 원칙 (Price-Time Priority)

부분 체결 및 잔량 처리, 취소 동작

동일 주문 처리에 대한 멱등성 (Idempotency)

2. Kafka 이벤트 흐름 검증
매칭 결과가 외부로 안전하게 전달되는지 확인합니다.

올바른 Topic/Key 발행 여부

재처리 시 이벤트 중복 발행 여부 및 멱등성

메시지 포맷(Contract) 준수 여부

3. 실제 운영 시나리오 (E2E)
Testcontainers를 활용하여 실제 환경과 유사하게 검증합니다.

Publish 실패 후 재시도 시나리오

강제 리밸런싱 및 워커 재시작

Snapshot 파일 손상/부재 시 복구 동작

목표: 중간에 실패하더라도 시스템이 결국 **일관된 상태로 돌아오는 회복력(Resilience)**을 확인합니다.

4. Snapshot 메커니즘 검증
Snapshot 유무에 따른 복구 속도 및 정합성 유지 여부 확인

"Snapshot = 최적화, Kafka 재처리 = 안전장치" 관계 검증

✅ 요약
이 매칭 엔진 구현체는 다음 원칙을 따릅니다.

실패를 기본 전제(Design for Failure)로 한다.

재처리(Retry)를 예외가 아닌 정상 경로로 취급한다.

Snapshot은 정합성이 아닌 속도를 위한 장치이다.

이를 통해 Kafka 환경에서 유지 가능한 현실적인 정합성의 경계를 명확히 정의했습니다.

