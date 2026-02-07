# 리스크 모듈 (Risk Module: B-1 ~ B-4)

이 모듈은 Trading Gate 백엔드의 **리스크 및 잔고 관리 시스템**을 구현합니다.
복잡한 PnL(손익)이나 포지션 계산 대신, **원장(Ledger) 기반의 정합성**에 초점을 맞춘 "단순화된 B(Simplified B)" 아키텍처를 따릅니다.

---

## 1. 핵심 철학 (변경 사항)

- **원장 중심 (Ledger Centrality)**: `LedgerEntry`(원장 항목)가 유일한 진실의 원천(SSOT)입니다.
- **자산 이동 (Asset Movement)**: 평균단가나 평가손익을 계산하는 대신, 자산의 이동(예: +BTC, -USDT)을 정확하게 기록합니다.
- **단순화 (Simplicity)**:
  - **B-1 (잔고/원장)**: 순수한 회계 처리 (분개, Journaling).
  - **B-2/B-3 (리스크)**: 최소한의 제어 (잔고 음수 체크, 기본적인 차단).
  - **B-4 (이상 감지)**: 패턴 로깅, 선택적 차단.

---

## 2. 아키텍처 및 데이터 흐름

핵심 로직은 `TradeProcessingOrchestrator`에 의해 조정되며, 트랜잭션 무결성과 멱등성을 보장합니다.

### **흐름: 체결 이벤트 처리 (Trade Execution Handling)**

`trades.executed` 이벤트를 수신했을 때의 처리 과정:

1.  **오케스트레이터 (`TradeProcessingOrchestrator`)**: 트랜잭션을 시작합니다.
2.  **Step 1: 원장 기록 (`LedgerService`)**
    - 체결 내용을 `LedgerEntry` 항목으로 기록합니다 (복식 부기 방식).
    - 예시 (BTC/USDT 매수):
      - `+0.1 BTC` (유형: TRADE)
      - `-5000 USDT` (유형: TRADE)
      - `-1.0 USDT` (유형: FEE)
    - **멱등성**: `tradeId` + `asset` + `type`을 조합한 키를 사용하여 중복 처리를 방지합니다.
3.  **Step 2: 잔고 업데이트 (`BalanceService`)**
    - 기록된 원가 항목을 기반으로 `AccountBalance` 프로젝션을 업데이트합니다.
    - 원자적 증감 연산 (`available` 잔고)을 수행합니다.
4.  **Step 3: 리스크 체크 (`RiskCheckService`)**
    - 업데이트 직후 잔고가 음수가 되었는지 확인합니다.
    - 위반 사항이 감지되면 `BalanceInsufficientEvent`를 발행합니다.

---

## 3. 컴포넌트 상세

### **1. TradeProcessingOrchestrator**

- **역할**: 체결 처리의 중앙 제어자.
- **책임**:
  - 트랜잭션 범위를 관리합니다.
  - Ledger, Balance, Risk 서비스 호출 순서를 조정합니다.
  - 예외 처리 및 트랜잭션 롤백을 담당합니다.
  - Ledger 결과를 확인하여 멱등성을 보장합니다.

### **2. LedgerService**

- **역할**: "진실(Truth)"인 원장의 관리자.
- **책임**:
  - 불변의 `LedgerEntry` 기록을 저장합니다.
  - 고유한 멱등성 키를 생성하여 중복 처리를 방지합니다.
  - 단일 `TradeExecutedEvent`를 다수의 원장 항목(기축 통화, 대조 통화, 수수료)으로 변환합니다.

### **3. BalanceService**

- **역할**: 잔고 프로젝션(Projection) 관리자.
- **책임**:
  - `AccountBalance`를 조회하고 업데이트합니다.
  - 여러 자산의 동시 업데이트를 처리합니다 (예: `Map<String, BigDecimal>`).
  - 잔고가 존재하지 않을 경우 새로 생성합니다.

### **4. RiskCheckService**

- **역할**: 트랜잭션 후 리스크 검증자.
- **책임**:
  - 업데이트 직후 음수 잔고 여부를 확인합니다.
  - 후속 조치(계정 차단, 관리자 알림 등)를 위해 `BalanceInsufficientEvent`를 발행합니다.

### **5. AccountBalance (Entity)**

- **역할**: 현재 상태의 스냅샷.
- **구조**:
  - 복합 키: `accountId` + `asset`.
  - 필드: `available`(사용 가능), `locked`(잠금), `updatedAt`(수정일).
  - 누적된 원장 합계의 캐시된 프로젝션 역할을 합니다.

---

## 4. 주요 로직 및 공식

### **잔고 프로젝션 (Balance Projection)**

```
현재 잔고 = 이전 잔고 + (마지막 업데이트 이후 원장 항목의 합)
```

_참고: 성능을 위해 `AccountBalance`를 반복적으로 업데이트하지만, 논리적으로는 항상 `LedgerEntry`의 합계와 일치해야 합니다._

### **멱등성 키 생성 (Idempotency Key Generation)**

```
Key = tradeId + "_" + asset + "_" + entryType
```

각 구체적인 자산 이동(예: 특정 거래의 수수료 차감)이 정확히 한 번만 기록되도록 보장합니다.
