# CHRONOS 구현 계획

> Cell-based Hexagonal Reactive Orchestration with Networked Ontology Sourcing — 레퍼런스 구현.
> 목표: 각 레이어의 핵심 개념을 **불변식 테스트로 증명**. 프로덕션 기능 아님.
>
> 이 저장소는 기존 tradexServer를 CHRONOS로 전환한 것이다. 레거시 코드는 커밋 `ee6ac1f`에 보존.

## 마일스톤별 산출물

각 마일스톤은 `./gradlew build` 그린 후 `M<n>: <요약>` 형식으로 커밋한다.

### M1 — 멀티모듈 skeleton + Konsist + L0 core (bi-temporal, 인메모리)

- `settings.gradle.kts` — rootProject `chronos`, 전 모듈 include
- `build.gradle.kts` — 루트(그룹/버전만)
- `buildSrc/` — convention plugins
  - `chronos.kotlin-library.gradle.kts` — JVM 21 툴체인, JUnit5, kotest
  - `chronos.spring-library.gradle.kts` — 위 + Spring BOM + spring plugin
- `chronos-core/src/main/kotlin/io/chronos/core/`
  - `event/UuidV7.kt` — 순수 Kotlin UUIDv7 생성기
  - `event/EventId.kt`, `event/AggregateId.kt` — value class
  - `event/DomainEvent.kt` — eventId/aggregateId/validTime/correctionOf
  - `event/EventRecord.kt` — 저장소 부여 메타(seqNo, transactionTime, globalSeq, cellId) 봉투
  - `aggregate/Aggregate.kt` — 순수 `evolve(state, event)` 추상화
  - `store/EventStore.kt` — append(낙관적 동시성)/readStream/readStreamAsAt/readAll 포트
  - `store/OptimisticConcurrencyException.kt`
  - `store/InMemoryEventStore.kt` — Clock 주입, 스레드 세이프
  - `query/AggregateRepository.kt` — 리플레이 + `stateAsOf(validTime)` / `stateAsAt(transactionTime)` + 정정 치환
  - `snapshot/SnapshotStore.kt` — 인터페이스만 (구현은 M2)
- `chronos-core/src/test/kotlin/` — UUIDv7 테스트, 낙관적 동시성 테스트, **bi-temporal 인수 테스트**(가격 정정 asOf/asAt/원본 보존)
- 나머지 모듈: build.gradle.kts + 빈 src (의존 방향만 선언)
- `chronos-runtime/src/test/kotlin/.../ArchitectureRulesTest.kt` — Konsist: core 순수성(Spring/Jackson import 0), 레이어 의존 방향

### M2 — Postgres 이벤트 스토어 + 스냅샷 + L1 membrane + 리플레이 게이트

- `chronos-membrane/src/main/kotlin/io/chronos/membrane/`
  - `EventSchema.kt` — `@EventSchema(type, version)` 애노테이션
  - `Upcaster.kt` — 인터페이스 + `UpcasterChain`(n→n+1 자동 조립, 결번 검출)
  - `EventSerde.kt` — Jackson 직렬화/역직렬화, 항상 최신 버전으로 승격 후 코어 전달
  - `SchemaRegistryScanner.kt` — classpath에서 @EventSchema 수집
  - `store/PostgresEventStore.kt` — event_store 테이블(JdbcClient), 낙관적 동시성 = UNIQUE(aggregate_id, seq_no)
  - `store/PostgresSnapshotStore.kt`
  - `schema.sql` — event_store / snapshot / projection_offset DDL
- `chronos-membrane/src/test/`
  - `fixtures/<type>/v<n>.json` — 전 버전 이벤트 샘플
  - `ReplayGateTest.kt` — fixture 전량 로드→upcast→역직렬화→evolve. @EventSchema 대비 fixture 누락 시 실패
  - `UpcasterChainTest.kt` — OrderPlaced v1(price)→v2(amount)→v3(+currency=KRW) 인수 테스트
  - `PostgresEventStoreTest.kt` — Testcontainers, bi-temporal 시나리오 재검증

### M3 — L2 사가 엔진 + DSL + 모델 체커

- `chronos-saga/src/main/kotlin/io/chronos/saga/`
  - `dsl/SagaDsl.kt` — `saga<Ctx>("name") { step("...") { action/compensate/timeout/retry } }`
  - `SagaDefinition.kt`, `SagaStep.kt`, `IdempotencyKey.kt` — (sagaId, stepName, attempt)
  - `engine/SagaEvents.kt` — SagaStarted/StepSucceeded/…/SagaCompensated (L0 DomainEvent)
  - `engine/SagaEngine.kt` — 이벤트소싱 실행기, 히스토리 리플레이로 재개 가능
  - `testkit/ModelChecker.kt` — step별 {성공,실패,타임아웃,보상실패} 전수 탐색(step≤6, 상한 초과 시 명시적 에러)
  - `testkit/InvariantDsl.kt` — `invariant("...") { trace -> ... }` + 반례 경로 pretty-print
- 테스트: 결정론적 리플레이, 멱등성 키 전달, **미처리 보상-타임아웃 반례를 모델 체커가 잡는 데모** + 수정본 통과 테스트

### M4 — L3 일관성 구배 라우터

- `chronos-router/src/main/kotlin/io/chronos/router/`
  - `ConsistencyLevel.kt` — STRONG / READ_YOUR_WRITES / EVENTUAL (기본 EVENTUAL)
  - `SessionToken.kt` — global_seq + HMAC 서명/검증
  - `ProjectionOffsetStore.kt` — 포트 + 인메모리/Postgres 구현
  - `Projector.kt` — 이벤트 → 프로젝션 갱신 루프(지연 주입 훅 포함)
  - `ConsistencyRouter.kt` — RYW 폴링(기본 2s 타임아웃 → 503 시맨틱), STRONG=스토어 직접 리플레이, EVENTUAL=즉시
- 테스트: 500ms 지연 주입 후 ① eventual stale 허용 ② RYW는 항상 자기 쓰기 관측 ③ strong 정확

### M5 — L4 셀 패브릭

- `chronos-cell/src/main/kotlin/io/chronos/cell/`
  - `ConsistentHashRing.kt` — 가상 노드
  - `Cell.kt` — {스토어 파티션 + 프로젝션 + 사가 엔진} 묶음, N=3 기동
  - `CellFabric.kt` — aggregateId→cell 라우팅, 셀 다운 시뮬레이션
  - `CellMigrator.kt` — 스트림 복사→캐치업→스위치→tombstone (마이그레이션 중 쓰기는 거절, DECISIONS 참조)
- 테스트: 마이그레이션 전후 상태 해시 동일, blast radius 격리

### M6 — L5 온톨로지 레지스트리

- `chronos-ontology/src/main/kotlin/io/chronos/ontology/`
  - `OntologyRegistry.kt` — `ontology/*.yaml` 로드 (term/type/version/fields/status)
  - `OntologyGuard.kt` — 미승인(비 APPROVED) 스키마 존재 시 기동 fail-fast
  - `OntologyValidator.kt` — @EventSchema ↔ YAML diff
- `buildSrc` 에 `ontologyValidate` Gradle task 등록, `check`에 연결
- 테스트: DRAFT 스키마 발행 차단, diff 검출

### M7 — runtime + example-app

- `chronos-runtime/` — `@EnableChronos` + AutoConfiguration으로 전 레이어 조립
- `example-app/` — 주문/결제/재고 (결제·재고 인메모리 fake 포트)
  - REST: 주문 생성, 주문 조회(X-Consistency 3종), 가격 정정, 사가 실패 스위치, 셀 마이그레이션 트리거, admin 이벤트 스트림
- `docker-compose.yml` — Postgres 16
- `README.md` — curl 전체 시나리오 (생성→RYW 조회→정정→asOf/asAt→결제실패→보상→마이그레이션→해시 비교)

## Definition of Done 체크리스트

- [ ] 전 모듈 `./gradlew build` 그린 (Konsist, 리플레이 게이트, ontologyValidate 포함)
- [ ] chronos-core에 Spring/Jackson import 0 (Konsist 검증)
- [ ] 모델 체커 반례 데모 테스트 ≥ 1
- [ ] bi-temporal asOf/asAt 차이 테스트 통과
- [ ] RYW가 프로젝션 지연 주입에도 자기 쓰기 관측
- [ ] 셀 마이그레이션 전후 상태 해시 동일
- [ ] README curl 시나리오가 `docker compose up` + 앱 기동만으로 재현
