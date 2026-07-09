# CHRONOS

**C**ell-based **H**exagonal **R**eactive **O**rchestration with **N**etworked **O**ntology **S**ourcing — 실험적 아키텍처의 레퍼런스 구현.

목표는 프로덕션 기능이 아니라 **각 레이어의 핵심 개념이 실제로 동작함을 불변식 테스트로 증명**하는 것이다.
계획은 [PLAN.md](PLAN.md), 설계 결정과 근거는 [DECISIONS.md](DECISIONS.md)에 있다.

## 레이어

| 모듈 | 레이어 | 증명하는 것 |
|---|---|---|
| `chronos-core` | L0 Bi-temporal Domain Core | valid-time/transaction-time 이원 시간축, 소급 정정, 순수 `evolve` 리플레이. Spring/Jackson import 0 (Konsist 강제) |
| `chronos-membrane` | L1 Schema Evolution Membrane | `@EventSchema` + upcaster 체인. 읽는 순간 항상 최신 버전으로 승격. 리플레이 게이트가 전 버전 fixture를 강제 |
| `chronos-saga` | L2 Deterministic Saga Engine | 엔진 자체가 이벤트소싱. 히스토리만으로 크래시 재개. **모델 체커**가 step별 결함 주입 전수 탐색(4^n)으로 불변식 위반 반례를 출력 |
| `chronos-router` | L3 Consistency Gradient Router | `X-Consistency: strong \| read-your-writes \| eventual`. HMAC 세션 토큰 + projection offset 대기 |
| `chronos-cell` | L4 Cell Fabric | 가상 노드 consistent hashing, 4단계 셀 마이그레이션(복사→캐치업→스위치→tombstone), blast radius 격리 |
| `chronos-ontology` | L5 Ubiquitous Language Registry | `ontology/*.yaml`. 미승인(DRAFT) 스키마는 기동 실패. `ontologyValidate` Gradle 태스크가 코드↔YAML diff로 빌드 게이트 |
| `chronos-runtime` | 조립 | `@EnableChronos` 하나로 전 레이어 와이어링 |
| `example-app` | 데모 | 주문/결제/재고 (결제·재고는 인메모리 fake 포트) |

## 빌드 & 테스트

```bash
# 전체 빌드 (Konsist 아키텍처 규칙, 리플레이 게이트, ontologyValidate, 모델 체커 포함)
# Postgres 통합 테스트는 Testcontainers를 쓰므로 Docker 데몬이 필요하다
./gradlew build
```

## 실행 (curl 시나리오)

전제: Docker, JDK 21. **저장소 루트에서** 실행한다.

```bash
docker compose up -d          # PostgreSQL 16 (localhost:55432, chronos/chronos — 로컬 PG와 충돌 방지)
./gradlew :example-app:bootRun
```

기동 로그에서 온톨로지 가드(L5)가 통과했는지 확인할 수 있다. `ontology/*.yaml`에서
스키마 하나를 `status: DRAFT`로 바꾸면 기동이 실패하는 것도 볼 수 있다 (fail-fast).

### 1. 주문 생성 → 사가(결제→재고→배송) 완료

```bash
RESP=$(curl -s -X POST localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"productName": "mechanical keyboard", "amount": 150000, "currency": "KRW"}')
echo "$RESP" | jq
ORDER=$(echo "$RESP" | jq -r .orderId)
SAGA=$(echo "$RESP" | jq -r .sagaId)
TOKEN=$(echo "$RESP" | jq -r .sessionToken)
```

`sagaOutcome: COMPLETED`, `status: CONFIRMED`, 그리고 HMAC 서명된 `sessionToken`이 온다.

### 2. read-your-writes 조회 — 항상 방금 쓴 값이 보인다

```bash
# 프로젝션 지연을 일부러 주입해도 (2초)
curl -s -X POST localhost:8080/admin/projection-delay \
  -H 'Content-Type: application/json' -d '{"delayMs": 1000}' | jq

# eventual은 stale을 볼 수 있지만 (빈 프로젝션이면 404)
curl -s localhost:8080/orders/$ORDER -H 'X-Consistency: eventual' | jq

# read-your-writes는 토큰의 seq까지 기다렸다가 반드시 자기 쓰기를 본다
curl -s localhost:8080/orders/$ORDER \
  -H 'X-Consistency: read-your-writes' -H "X-Session-Token: $TOKEN" | jq

# strong은 프로젝션을 우회해 이벤트 스토어에서 직접 리플레이한다
curl -s localhost:8080/orders/$ORDER -H 'X-Consistency: strong' | jq

# 지연 원복
curl -s -X POST localhost:8080/admin/projection-delay \
  -H 'Content-Type: application/json' -d '{"delayMs": 0}' > /dev/null
```

지연을 RYW 타임아웃(2s)보다 크게 주면 `503 + Retry-After`를 받는다.

### 3. 가격 정정 (bi-temporal) → as-of / as-at 비교

```bash
T_BEFORE=$(date -u +%Y-%m-%dT%H:%M:%SZ)   # 정정 이전 시각을 기억
sleep 1

# 소급 정정: "사실 그 주문의 금액은 135,000이었다"
curl -s -X POST localhost:8080/orders/$ORDER/price-correction \
  -H 'Content-Type: application/json' -d '{"amount": 135000}' | jq

# asOf(지금): 정정이 소급 반영된 진실 → 135000
curl -s "localhost:8080/orders/$ORDER/as-of?at=$(date -u +%Y-%m-%dT%H:%M:%SZ)" | jq .amount

# asAt(정정 이전): 그 당시 시스템이 알던 모습 → 150000
curl -s "localhost:8080/orders/$ORDER/as-at?at=$T_BEFORE" | jq .amount

# 원본 이벤트 로우는 물리적으로 보존된다 (correctionOf 링크 확인)
curl -s localhost:8080/admin/orders/$ORDER/events | jq '.[] | {seqNo, eventType, correctionOf, payload}'
```

### 4. 결제 실패 유발 → 사가 보상 확인

```bash
curl -s -X POST localhost:8080/admin/payment-mode \
  -H 'Content-Type: application/json' -d '{"mode": "FAIL"}' | jq

RESP2=$(curl -s -X POST localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"productName": "gaming mouse", "amount": 89000}')
echo "$RESP2" | jq '{sagaOutcome, status}'    # COMPENSATED / CANCELLED

# 사가 엔진 자체가 이벤트소싱 — 재시도 소진과 보상 히스토리가 그대로 남는다
SAGA2=$(echo "$RESP2" | jq -r .sagaId)
curl -s localhost:8080/admin/orders/$SAGA2/events | jq '.[].eventType'

# 원복
curl -s -X POST localhost:8080/admin/payment-mode \
  -H 'Content-Type: application/json' -d '{"mode": "OK"}' > /dev/null
```

`mode: TIMEOUT`으로 바꾸면 `StepTimedOut` 경로(재시도 → 소진 → 보상)도 볼 수 있다.

### 5. 셀 마이그레이션 → 상태 해시 동일 확인

```bash
# 현재 셀과 상태 해시
curl -s localhost:8080/admin/orders/$ORDER/state-hash | jq
CELL=$(curl -s localhost:8080/admin/orders/$ORDER/state-hash | jq .cellId)

# 다른 셀로 이관: ① 스트림 복사 ② 오프셋 캐치업 ③ 라우팅 스위치 ④ 소스 tombstone
curl -s -X POST localhost:8080/admin/orders/$ORDER/migrate \
  -H 'Content-Type: application/json' -d "{\"targetCell\": $(( (CELL + 1) % 3 ))}" | jq

# identical: true — 상태가 아니라 이벤트가 진실이므로, 스트림 리플레이만으로 상태가 복원된다
curl -s localhost:8080/admin/orders/$ORDER/state-hash | jq
curl -s localhost:8080/orders/$ORDER -H 'X-Consistency: strong' | jq
```

Postgres에서 직접 확인: 셀 파티션은 테이블로 분리되어 있다.

```bash
docker exec chronos-postgres psql -U chronos -c '\dt event_store*'
docker exec chronos-postgres psql -U chronos -c \
  'SELECT seq_no, event_type, event_version, correction_of, transaction_time FROM event_store_0 ORDER BY global_seq LIMIT 10;'
```

## 핵심 테스트 지도

| 불변식 | 테스트 |
|---|---|
| asOf/asAt 차이 + 원본 보존 | `chronos-core BiTemporalAcceptanceTest`, `chronos-membrane PostgresEventStoreTest` |
| v1→v2→v3 업캐스트 체인 | `chronos-membrane UpcasterChainTest` |
| fixture 강제 (리플레이 게이트) | `chronos-membrane ReplayGateTest`, `example-app ExampleReplayGateTest` |
| 사가 결정론적 리플레이/크래시 재개 | `chronos-saga SagaEngineTest` |
| **모델 체커 반례 (보상 타임아웃 미처리)** | `chronos-saga ModelCheckerTest` — 반례 경로가 사람이 읽게 출력된다 |
| RYW가 지연 주입에도 자기 쓰기 관측 | `chronos-router ConsistencyGradientAcceptanceTest` |
| 마이그레이션 전후 상태 해시 동일 | `chronos-cell CellMigrationTest`, `example-app EndToEndScenarioTest` |
| blast radius 격리 | `chronos-cell CellFabricTest` |
| 의존 방향 + core 순수성 | `chronos-runtime ArchitectureRulesTest` (Konsist) |
| 코드↔온톨로지 drift | `./gradlew ontologyValidate` (check에 연결) |
