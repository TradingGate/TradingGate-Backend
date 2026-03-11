# Local Kubernetes Deployment

이 디렉터리는 Docker Desktop Kubernetes / `k3d` / `kind` 같은 로컬 Kubernetes 환경에서
`api / worker / risk / clearing` 애플리케이션을 배포하기 위한 매니페스트를 담고 있다.

## 전제
- 애플리케이션 이미지는 로컬 Docker에 `tradinggate-backend:local` 태그로 빌드한다.
- Kafka / Postgres / Redis는 기존 `docker-compose.yml`로 먼저 띄운다.
- 기본 호스트 값은 Docker Desktop 기준 `host.docker.internal`이다.
  - `k3d` 사용 시 `configmap.yaml`의 호스트를 `host.k3d.internal`로 바꾸는 편이 안전하다.

## 1. 인프라 기동
```bash
docker compose up -d
```

## 2. 애플리케이션 이미지 빌드
```bash
docker build -t tradinggate-backend:local .
```

## 3. Secret 생성
샘플을 복사해 실제 시크릿 파일을 만든다.
```bash
cp k8s/local/secret.sample.yaml k8s/local/secret.yaml
kubectl apply -f k8s/local/secret.yaml
```

## 4. 매니페스트 적용
```bash
kubectl apply -k k8s/local
```

## 5. 상태 확인
```bash
kubectl get pods -n tradinggate
kubectl get svc -n tradinggate
```

## 6. API 접근
NodePort 기준:
- `http://localhost:30080`

예시:
```bash
curl -X POST 'http://localhost:30080/api/orders/create' \
  -H 'Content-Type: application/json' \
  --data '{"clientOrderId":"k8s-test-1","symbol":"BTCUSDT","orderSide":"BUY","orderType":"LIMIT","timeInForce":"GTC","price":"50000","quantity":"1"}'
```

## 운영 메모
- `worker`, `risk`, `clearing`는 런타임 프로필만 다르고 같은 이미지를 재사용한다.
- `recon`은 별도 프로세스로 두지 않고 `clearing` 프로필 내부에서 함께 동작한다.
- 헬스체크는 보안/액추에이터 설정과 독립적으로 동작하도록 TCP probe를 사용한다.

## 구성 의도
이 디렉터리는 **앱만 Kubernetes에 배포**하고, 상태 저장 인프라(Kafka / Postgres / Redis)는
외부 Docker 환경이나 관리형 서비스가 담당하는 구조를 가정한다.

- 로컬 app-only 배포: `k8s/local`
- 로컬 all-in-k8s 데모: `k8s/local-all`

운영(EKS) 가정에서는 이 `app-only` 구성이 더 실무적이고,
`local-all`은 포트폴리오/데모 재현성을 위한 보조 구성으로 보는 것이 맞다.
