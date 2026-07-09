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

## 빌드 & 테스트

```bash
# 전체 빌드 (Konsist 아키텍처 규칙, 리플레이 게이트, ontologyValidate, 모델 체커 포함)
# Postgres 통합 테스트는 Testcontainers를 쓰므로 Docker 데몬이 필요하다
./gradlew build
```

## tradex-* 서비스 — 레거시 기능의 CHRONOS 이식 (MSA)

레거시 tradexServer(커밋 `ee6ac1f`)의 인증/회원가입/멤버를 CHRONOS 위에 **기능별 독립 서비스**로
재구현했다. 각 서비스는 자기 CHRONOS 패브릭(전용 Postgres 테이블 프리픽스)을 갖고, 서로 HTTP로만
통신한다 — 프로세스 경계가 곧 Bounded Context 경계다.

| 서비스 | 포트 | 소유 애그리게잇 | 책임 |
|---|---|---|---|
| `tradex-auth-service` | 8081 | User | 로그인/토큰 회전/사인아웃/검증. `/internal/*`로 프로비저닝 API 노출 |
| `tradex-member-service` | 8082 | Member | PII 암호화 저장·유니크 검사. `/internal/*`로 프로비저닝 API 노출 |
| `tradex-registration-service` | 8083 | (상태 없음, 사가만) | `RegisterAccount` 사가 오케스트레이터 — auth/member를 HTTP로 호출 |

API 계약(BaseResponse 봉투, refresh HttpOnly 쿠키, 경로)과 도메인 규칙(비밀번호 12자+3종,
5회 실패 30분 잠금, E.164 정규화, 만 14세)은 레거시 그대로다. 매핑 근거는 DECISIONS.md D17~D22.

| 레거시 | CHRONOS 재구현 |
|---|---|
| 단일 앱의 JPA User 엔티티 | auth-service의 `UserAggregate` 이벤트 리플레이 |
| Redis refresh 스토어 + 블랙리스트 | User 스트림의 `RefreshTokenIssued/Revoked`, `AccessTokenBlacklisted` |
| 등록 = 단일 `@Transactional` (한 프로세스) | registration-service의 **서비스 간 사가** — auth/member를 HTTP로 호출, 실패 시 역순 보상(DELETE). **모델 체커가 16경로 전수로 "계정과 멤버는 함께 존재하거나 함께 사라진다"를 증명** |
| DB unique 제약 (email/phone) | 각 서비스 내부 인덱스 프로젝션 + 동기 catch-up 검사 |
| PII 암호화 컬럼 | member-service 소유 AES-GCM 암호문만 이벤트에 (crypto-shredding 경로 유지) |

### 실행

```bash
docker compose up -d                        # PostgreSQL (localhost:55432, chronos/chronos)
./gradlew :tradex-auth-service:bootRun &
./gradlew :tradex-member-service:bootRun &
./gradlew :tradex-registration-service:bootRun &
```

세 프로세스 모두 기동 로그에서 온톨로지 가드(L5)를 통과해야 한다. secrets/jwt-*.pem이 있으면
auth-service가 그 키로 RS256을 서명하고, 없으면 임시 키쌍을 생성한다(재기동 시 토큰 전부 무효화).

### curl 시나리오 — 가입 → 로그인 → 회전 → 재사용 감지 → 사인아웃

```bash
# 가입: registration-service(8083)가 auth-service(8081)·member-service(8082)를 HTTP로 호출
curl -s -X POST localhost:8083/api/v1/registration -H 'Content-Type: application/json' \
  -d '{"email":"me@tradex.io","password":"Sup3r$ecretPw!","name":"김제리","birthDate":"1995-03-14","phoneNumber":"010-1234-5678"}'

# 중복 이메일 → auth-service의 409가 registration을 거쳐 그대로 전달된다
curl -s -w '\n%{http_code}\n' -X POST localhost:8083/api/v1/registration -H 'Content-Type: application/json' \
  -d '{"email":"me@tradex.io","password":"Sup3r$ecretPw!","name":"김","birthDate":"1995-03-14","phoneNumber":"010-9999-0000"}'

# 로그인은 auth-service에 직접 (registration은 등록 전용, 인증에 관여하지 않는다)
curl -s -c /tmp/c.txt -X POST localhost:8081/api/v1/auth/sign-in -H 'Content-Type: application/json' \
  -d '{"email":"me@tradex.io","password":"Sup3r$ecretPw!"}'          # data.accessToken + refresh 쿠키
curl -s localhost:8081/api/v1/auth/me -H "Authorization: Bearer $ACCESS"
curl -s -b /tmp/c.txt -c /tmp/c2.txt -X POST localhost:8081/api/v1/auth/reissue   # 회전
curl -s -b /tmp/c.txt -X POST localhost:8081/api/v1/auth/reissue                  # 옛 토큰 재사용 → 401 + 전면 폐기
curl -s -b /tmp/c2.txt -X POST localhost:8081/api/v1/auth/sign-out -H "Authorization: Bearer $ACCESS"
```

### blast radius 데모 — auth-service를 죽여도 그 사실이 정확히 전파된다

```bash
kill $(lsof -tiTCP:8081 -sTCP:LISTEN)     # auth-service만 다운

# member-service는 살아있지만, 등록 사가의 첫 step(auth 호출)이 실패해 전체가 실패한다
# member 쪽에는 고아 이벤트가 전혀 남지 않는다 (프리페어 단계에서 이미 중단)
curl -s -w '\n%{http_code}\n' -X POST localhost:8083/api/v1/registration -H 'Content-Type: application/json' \
  -d '{"email":"during-outage@tradex.io","password":"Sup3r$ecretPw!","name":"김","birthDate":"1995-03-14","phoneNumber":"010-1111-2222"}'

./gradlew :tradex-auth-service:bootRun &   # 복구 후에는 다시 정상 등록된다
```

### 서비스별 이벤트 감사 로그 확인

```bash
docker exec chronos-postgres psql -U chronos -c \
  "SELECT seq_no, event_type FROM tradex_auth_event_store_0 WHERE aggregate_type='User' ORDER BY seq_no;"
docker exec chronos-postgres psql -U chronos -c \
  "SELECT seq_no, event_type FROM tradex_member_event_store_0 WHERE aggregate_type='Member' ORDER BY seq_no;"
docker exec chronos-postgres psql -U chronos -c \
  "SELECT seq_no, event_type FROM tradex_registration_event_store_0 WHERE aggregate_type='Saga' ORDER BY seq_no;"
```

## 핵심 테스트 지도

| 불변식 | 테스트 |
|---|---|
| asOf/asAt 차이 + 원본 보존 | `chronos-core BiTemporalAcceptanceTest`, `chronos-membrane PostgresEventStoreTest` |
| v1→v2→v3 업캐스트 체인 | `chronos-membrane UpcasterChainTest` |
| fixture 강제 (리플레이 게이트) | `chronos-membrane ReplayGateTest`, 각 `tradex-*-service`의 `*ReplayGateTest` |
| 사가 결정론적 리플레이/크래시 재개 | `chronos-saga SagaEngineTest` |
| **모델 체커 반례 (보상 타임아웃 미처리)** | `chronos-saga ModelCheckerTest` — 반례 경로가 사람이 읽게 출력된다 |
| **서비스 간 등록 사가 — 유령 계정 금지 (16경로 전수)** | `tradex-registration-service RegistrationSagaModelCheckTest` |
| RYW가 지연 주입에도 자기 쓰기 관측 | `chronos-router ConsistencyGradientAcceptanceTest` |
| 마이그레이션 전후 상태 해시 동일 | `chronos-cell CellMigrationTest` |
| blast radius 격리 | `chronos-cell CellFabricTest`, 위 curl 데모(auth-service 다운) |
| 인증 수명 주기(로그인/잠금/회전/재사용감지/블랙리스트) | `tradex-auth-service AuthServiceE2ETest`, `UserAggregateTest` |
| PII 암호화 + 전화 유니크(E.164 정규화) | `tradex-member-service MemberServiceE2ETest` |
| 등록 오케스트레이션 + 다운스트림 거절 전파 | `tradex-registration-service RegistrationServiceE2ETest` |
| 의존 방향 + core 순수성 + 서비스별 도메인 격리 | `chronos-runtime ArchitectureRulesTest` (Konsist) |
| 코드↔온톨로지 drift | `./gradlew ontologyValidate` (check에 연결) |
