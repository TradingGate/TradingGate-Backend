# Local All-in-Kubernetes Demo

이 디렉터리는 로컬 Kubernetes 하나에서 애플리케이션과 인프라를 함께 띄우는 데모 구성을 담고 있다.

구성 대상:
- 앱: `api`, `worker`, `risk`, `clearing`
- 인프라: `trading-postgres`, `ledger-postgres`, `redis`, `kafka`

## 목적
- `kubectl apply`만으로 전체 구조를 재현하는 데모 환경 제공
- 포트폴리오에서 아키텍처 전체를 한 번에 시연할 수 있게 함

## 전제
- 로컬 Docker 이미지 `tradinggate-backend:local`이 빌드되어 있어야 한다.
- 이 구성은 데모/학습용 단일 노드 환경이다.
- 영속성/복구/확장성보다는 재현성과 단순성을 우선한다.

## 실행 순서
```bash
docker build -t tradinggate-backend:local .
cp k8s/local-all/secret.sample.yaml k8s/local-all/secret.yaml
kubectl apply -f k8s/local-all/secret.yaml
kubectl apply -k k8s/local-all
```

## 상태 확인
```bash
kubectl get pods -n tradinggate-demo
kubectl get svc -n tradinggate-demo
```

## API 접근
NodePort 기준:
- `http://localhost:31080`

예시:
```bash
curl -X POST 'http://localhost:31080/api/orders/create' \
  -H 'Content-Type: application/json' \
  --data '{"clientOrderId":"demo-all-k8s-1","symbol":"BTCUSDT","orderSide":"BUY","orderType":"LIMIT","timeInForce":"GTC","price":"50000","quantity":"1"}'
```

## 최종 검증 결과

- `api -> worker -> projection -> risk -> clearing/recon` 전체 흐름을 `local-all`에서 실제로 검증했다.
- 교차 사용자 주문 2건(`X-User-Id` 기준)으로 체결을 발생시켜 아래를 확인했다.
  - `trading_order` 상태 `FILLED`
  - `trading_trade` 저장
  - `ledger_entry`, `account_balance` 반영
- 현재 매칭 엔진은 정수 수량 기준으로만 안전하므로 데모 검증은 `quantity=1` 기준으로 수행했다.

## 운영 가정과의 차이
이 디렉터리는 로컬 데모용이다. Kafka 호환 브로커는 데모 단순화를 위해 단일 노드 `Redpanda`로 둔다.
실제 운영(EKS)에서는 아래처럼 분리하는 쪽이 더 현실적이다.
- 애플리케이션: EKS (`api`, `worker`, `risk`, `clearing`)
- 상태 저장 인프라: RDS(Postgres), ElastiCache(Redis), MSK/Kafka

즉, 로컬 데모에서는 재현성을 위해 인프라를 함께 올리고,
운영 가정에서는 상태 저장소를 관리형 서비스로 분리하는 전략을 전제로 한다.
