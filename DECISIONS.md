# DECISIONS

명세가 위임했거나 모호했던 지점의 결정 기록. 형식: 결정 / 근거 / 대안.

## D1. 기존 tradexServer 코드 처리

- **결정**: 저장소를 CHRONOS로 전환하되, 레거시 전체를 커밋 `ee6ac1f`(`legacy: snapshot ...`)에 보존 후 워킹트리에서 제거. `.env`, `secrets/`는 gitignore 상태 그대로 로컬에 유지.
- **근거**: 사용자가 "tradexServer를 CHRONOS로 전환"을 선택. 레거시(인증/회원, Redis/JPA)는 CHRONOS 금지 스택(Redis)과 충돌하고 예제 도메인(주문/결제/재고)과 무관하므로 빌드에 남기지 않는다. 복원은 git으로 가능.

## D2. 영속성: Spring Data JDBC(JdbcClient) 선택, jOOQ 배제

- **결정**: `spring-boot-starter-data-jdbc`의 `JdbcClient`로 손으로 쓴 SQL 실행. 리포지토리 추상화(엔티티 매핑)는 쓰지 않는다.
- **근거**: 이벤트 스토어는 append-only INSERT + 몇 개의 범위 SELECT가 전부라 타입세이프 쿼리 빌더(jOOQ)의 코드젠·빌드 파이프라인 비용이 v1 범위에 비해 과함. JSONB·`BIGSERIAL` 등 PG 전용 구문은 raw SQL이 가장 투명하다.
- **대안**: 쿼리가 수십 개로 늘고 조인이 생기면 jOOQ 재평가. `// TODO(v2)`.

## D3. `seqNo`/`transactionTime`의 위치 — 도메인 이벤트가 아닌 저장 봉투(EventRecord)

- **결정**: 명세는 "모든 이벤트는 seqNo를 갖는다"라고 하나, `seqNo`·`transactionTime`·`globalSeq`·`cellId`는 **저장소가 부여하는 사실**이므로 `EventRecord<E>` 봉투에 둔다. `DomainEvent`는 eventId/aggregateId/validTime/correctionOf만 가진다.
- **근거**: 도메인 이벤트를 생성 시점에 완결된 불변 값으로 유지(순수 코어). 부여 전 seqNo가 null인 상태를 도메인 타입에 허용하면 evolve 순수성과 타입 안전이 깨진다. 봉투를 통해 명세의 "이벤트는 seqNo를 갖는다"는 조회 관점에서 그대로 성립한다.

## D4. `DomainEvent`를 sealed로 만들지 않음

- **결정**: 코어의 `DomainEvent`는 일반 인터페이스. 각 애플리케이션이 `sealed interface OrderEvent : DomainEvent`처럼 자기 도메인에서 sealed 계층을 만든다.
- **근거**: Kotlin sealed는 같은 컴파일 유닛으로 서브타입을 제한하므로 코어에서 sealed면 바깥 모듈이 이벤트를 정의할 수 없다. 명세의 "sealed interface 기반"은 도메인별 이벤트 계층에 적용하는 것으로 해석.

## D5. 정정(correction) 시맨틱

- **결정**: 정정 이벤트 = **원본과 같은 타입**의 새 이벤트 + `correctionOf = 원본 eventId`. 리플레이 시 원본의 스트림 위치(원본 seqNo 순서)에서 정정본 페이로드로 치환해 evolve한다. 정정의 정정은 체인을 끝까지 따라간다. 원본 로우는 절대 변경/삭제하지 않는다.
  - `stateAsOf(validTime)`: 현재 알려진 전체 지식에서 정정 치환 후, 치환된 이벤트의 validTime ≤ t 필터 → 리플레이.
  - `stateAsAt(txTime)`: transactionTime ≤ T 인 레코드만으로 같은 절차 → "그 당시 시스템이 알던 모습". T가 정정 기록 이전이면 자연히 정정 미반영.
- **근거**: evolve를 정정 존재와 무관한 순수 함수로 유지(치환은 리플레이어 책임). 별도 "PriceCorrected" 타입 방식은 모든 애그리게잇이 정정 이벤트를 이해해야 해 코어 목표와 충돌.

## D6. Gradle/Kotlin 버전

- **결정**: Gradle 9.4.1(기존 wrapper 유지) + Kotlin 2.2.20 + Spring Boot 3.5.14(BOM). `io.spring.dependency-management` 대신 `platform()` BOM 사용.
- **근거**: 명세는 Kotlin 2.1+만 고정. Gradle 9.x는 KGP 2.2+가 필요하므로 2.2.20 선택. dependency-management 플러그인은 Gradle 네이티브 platform으로 대체 가능해 의존을 줄임.

## D7. Konsist 아키텍처 테스트 위치

- **결정**: `chronos-runtime/src/test`에 배치(전 레이어를 아는 유일한 모듈). `Konsist.scopeFromProject()`로 루트 전체 스캔.
- **근거**: 별도 arch-test 모듈은 명세의 고정 모듈 구조에 없음.

## D8. event_store에 `event_id UUID UNIQUE` 컬럼 추가 (스케치 대비 변경)

- **결정**: 명세의 DDL 스케치에 `event_id` 컬럼을 추가했다.
- **근거**: `DomainEvent.correctionOf`는 EventId를 가리키는데 스케치의 `correction_of`는 global_seq FK다. eventId → global_seq 해석과 정정 대상 검증(존재·타입 일치)을 위해 조회 가능한 UNIQUE 컬럼이 필요하다. 명세 스스로 "조정 가능, 의도 유지"를 허용.

## D9. Postgres 어댑터를 chronos-membrane에 배치

- **결정**: `PostgresEventStore`/`PostgresSnapshotStore`는 `io.chronos.membrane.store`에 둔다.
- **근거**: 저장된 표현(JSON + 버전)과 도메인 객체 사이의 변환이 곧 진화막의 일이다. 스토어는 읽는 순간 upcast를 강제하는 지점이므로 serde와 같은 모듈에 있어야 "옛 스키마가 코어에 새는" 경로가 원천 차단된다. 의존 방향(membrane → core)도 유지된다.

## D10. 셀 마이그레이션 중 쓰기: **거절(REJECTED)** (M5)

- **결정**: 마이그레이션 중 해당 aggregate 쓰기는 `MigrationInProgressException`으로 즉시 거절. 클라이언트 재시도 책임.
- **근거**: 큐잉은 큐 내구성·순서·중복 문제를 v1에 끌어들인다. 거절은 시맨틱이 단순하고 테스트로 증명하기 쉬우며, 이벤트 스트림 복사의 일관성 컷오프가 명확해진다. `// TODO(v2): write queueing`.

## D11. 사가 이벤트에 `CompensationSucceeded` 추가 (명세 목록 대비 +1)

- **결정**: 명세의 8종에 `CompensationSucceeded(stepName, attempt)`를 추가했다.
- **근거**: step 단위 보상 진행을 히스토리만으로 복원(결정론적 리플레이·재개)하려면 "어느 step의 보상이 끝났는지"가 이벤트로 남아야 한다. 다음 CompensationStarted로 유추하는 방식은 마지막 step 보상 완료를 표현할 수 없다.

## D12. 모델 체커 주입 시맨틱: TIMEOUT=일시 장애, FAIL=영구 실패

- **결정**: `TIMEOUT`(및 `COMPENSATION_TIMEOUT`)은 첫 시도만 실패하고 재시도가 있으면 성공하는 일시 장애로, `FAIL`은 전 시도가 실패하는 영구 실패로 모델링했다.
- **근거**: 이렇게 해야 "보상 재시도 미설정" 결함이 반례로 잡히고(첫 보상 타임아웃 → 소진 → STUCK), 수정본(compensationRetry(3))이 전 경로(4^n)를 실제로 통과한다. 모든 결함을 영구로 모델링하면 어떤 정의도 통과할 수 없어 체커가 무의미해진다.
- 보상 재시도 소진 시 엔진은 터미널 이벤트 없이 STUCK을 반환한다 — 보상이 끝나지 않았는데 SagaCompensated를 쓰는 것은 거짓 기록이기 때문. STUCK 방지는 정의 작성자의 책임이며 모델 체커가 그 증명 도구다.

## D13. ontologyValidate는 소스 정규식 스캔 (리플렉션 스캔 아님)

- **결정**: Gradle 태스크는 각 모듈 `src/main/kotlin`을 정규식으로 스캔해 `@EventSchema(type, version)`을 수집한다. 런타임 기동 가드(OntologyGuard)는 EventSchemaRegistry(리플렉션 기반)로 같은 검사를 이중으로 수행한다.
- **근거**: buildSrc가 프로젝트 모듈의 컴파일 산출물에 의존하면 순환이 생긴다. 애노테이션 표기는 named-argument 형식으로 컨벤션이 고정돼 있어 정규식으로 안전하고, 빌드 게이트(태스크)와 기동 게이트(가드)가 서로를 보완한다. 네거티브 검증 완료(버전 변조 → 빌드 실패 확인).

## D14. 역직렬화는 미지 필드를 무시 (FAIL_ON_UNKNOWN_PROPERTIES off)

- **결정**: ChronosJson mapper는 모르는 JSON 필드를 무시한다.
- **근거**: 진화막의 forward-compatibility — 새 필드가 추가된 페이로드를 구버전 코드가 읽어도 깨지지 않아야 한다. 인터페이스 기본 프로퍼티(correctionOf 등)가 직렬화엔 포함되지만 생성자에 없는 클래스(사가 이벤트)의 왕복도 이걸로 성립한다. 필수 필드 누락은 Kotlin 생성자 바인딩이 여전히 실패시키므로 upcaster 결번은 감춰지지 않는다. 회귀 테스트: `SagaEventRoundTripTest`.

## D15. Postgres 셀 파티션 = 테이블 분리 (event_store_0..N)

- **결정**: `chronos.storage=POSTGRES`일 때 셀마다 `event_store_<cellId>` 테이블을 생성한다.
- **근거**: 단일 테이블 + cell_id 컬럼 방식은 마이그레이션의 "스트림 복사"가 UNIQUE(aggregate_id, seq_no)와 충돌해 물리적으로 불가능하다. 테이블 분리는 파티션 격리를 정직하게 모델링하고 복사·tombstone·blast radius가 실제로 성립한다.

## D16. docker-compose 호스트 포트 55432

- **결정**: 데모 Postgres는 호스트 55432에 바인딩한다 (컨테이너 내부는 5432).
- **근거**: 개발 머신에 로컬 PostgreSQL이 5432를 점유한 경우가 흔해(이 저장소의 개발 환경 포함) 기본 포트는 `docker compose up`부터 실패한다.

## D17. 레거시 tradexServer 기능의 CHRONOS 재구현 — 별도 `tradex-app` 모듈

- **결정**: 인증/회원가입/멤버를 example-app과 분리된 `tradex-app` 모듈로 재구현한다. API 계약(경로 `/api/v1/registration`, `/api/v1/auth/*`, BaseResponse 봉투, refresh HttpOnly 쿠키)과 도메인 규칙(비밀번호 정책 12자+3종, 잠금 5회/30분, 이메일 정규화, 전화 E.164 정규화, 만 14세)은 레거시(ee6ac1f)와 동일하게 유지한다.
- **근거**: example-app은 명세가 고정한 주문/결제/재고 데모라 오염시키지 않는다. Spring Security 필터 체인은 레퍼런스 범위 밖이라 제외하고, ValidateAccessTokenUseCase는 `GET /api/v1/auth/me` 엔드포인트로 표면화한다.

## D18. Redis refresh 토큰 스토어 → User 애그리게잇 이벤트로 대체

- **결정**: 레거시의 Redis(userId→jti, 블랙리스트 TTL)를 User 스트림의 `RefreshTokenIssued/RefreshTokenRevoked/AccessTokenBlacklisted` 이벤트로 대체한다. 회전(reissue) = Revoked+Issued 원자 배치, 재사용 감지(jti 불일치) = 활성 토큰 방어적 폐기, TTL = 상태의 expiresAt을 조회 시각과 비교.
- **근거**: CHRONOS v1은 Redis 금지. 단일 활성 세션 정책(레거시와 동일)이라 상태가 작고, 토큰 수명 주기가 감사 가능한 이벤트 히스토리로 남는 것이 오히려 이득이다.

## D19. 잠금 결정은 이벤트에 기록 (SignInFailed가 failureCount·lockedUntil을 실어 나름)

- **결정**: 로그인 실패 시 서비스가 LockPolicy로 다음 상태를 **결정**해 `SignInFailed(failureCount, lockedUntil)`에 기록하고, evolve는 그대로 반영만 한다. 잠금 해제는 이벤트 없이 `lockedUntil < now` 파생으로 처리한다(레거시의 상태 변이식 auto-unlock 대체).
- **근거**: evolve에 정책을 넣으면 정책 변경이 과거 리플레이 결과를 바꾼다. 결정은 기록하고 파생은 계산한다.

## D20. 이메일/전화 유니크 제약 → 인덱스 프로젝션 + 동기 catch-up 검사

- **결정**: 전 셀 스토어를 소비하는 email/phoneHash 인덱스 프로젝션을 두고, 등록 직전 동기 catch-up 후 검사한다.
- **근거**: 이벤트소싱에서 애그리게잇 경계를 넘는 유니크는 원천적으로 조회 모델 문제다. 단일 JVM v1에서는 동기 catch-up으로 사실상 선형화된다. 동시 등록 race는 v1 한계로 명시한다. `// TODO(v2): reservation aggregate`.

## D21. 계정 등록 = 사가 (레거시 단일 @Transactional의 대체) + PII는 이벤트에 암호문으로

- **결정**: User 등록과 Member 생성은 서로 다른 애그리게잇(잠재적으로 다른 셀)이므로 `RegisterAccount` 사가(registerUser → createMember, 보상 = 각각 Revoked 이벤트)로 묶고, 모델 체커로 "계정과 멤버는 함께 존재하거나 함께 사라진다" 불변식을 전 경로 검증한다. PII(이름/생일/전화)는 레거시처럼 AES-GCM 암호문으로만 이벤트에 저장하고 전화 유니크는 HMAC 해시로 잡는다.
- **근거**: 불변 이벤트 로그에 평문 PII를 넣으면 삭제 요구에 대응 불가(crypto-shredding 경로 유지). JWT RS256 키는 `secrets/jwt-*.pem`이 있으면 로드, 없으면 기동 시 임시 생성(테스트/데모용).

## D22. tradex-app 단일 배포체 → 3개 독립 서비스로 재분리 (MSA)

- **결정**: `tradex-app`을 없애고 `tradex-auth-service`(8081, User 소유), `tradex-member-service`(8082, Member 소유), `tradex-registration-service`(8083, 상태 없는 사가 오케스트레이터)로 분리했다. 각 서비스는 전용 Postgres 테이블 프리픽스(`tradex_auth_event_store_*` 등)로 완전히 격리된 자기 CHRONOS 패브릭을 갖는다. `example-app`은 삭제.
- **근거**: 사용자가 "기능 한 개당 따로따로 MSA 느낌"을 명시적으로 요청. 프로세스 경계를 Bounded Context 경계와 일치시키는 것이 CHRONOS L5(온톨로지)의 취지와도 맞는다 — 이전에는 한 JVM 안에서 모듈로만 나뉘어 있어 "서비스 장애가 다른 서비스에 어떤 영향을 주는가"를 증명할 수 없었다.
- **API 경계**: auth/member 각각 `/internal/*`(프로비저닝: prepare + PUT/DELETE 멱등)을 registration 전용으로 노출한다. registration은 상태를 갖지 않고 사가 이벤트만 자기 스토어에 남긴다.
- **평문 비밀 전파 최소화**: 평문 비밀번호/PII는 각 소유 서비스의 prepare 엔드포인트 호출에서만 네트워크를 넘고, 그 응답(해시/암호문)만 사가 컨텍스트에 실린다 — registration-service는 평문을 저장하지도, 재전송하지도 않는다.
- **장애 모델**: 다운스트림 4xx 거절은 `ProvisioningRejectedException`으로 변환되어 사가에서 영구 실패로 취급되고, HTTP 응답의 status/code/message가 그대로 클라이언트까지 전달된다. 5xx/연결 실패(서비스 다운)는 그대로 전파되어 즉시 등록 실패 + 재시도 대상이 된다 — 실제로 auth-service를 강제 종료한 뒤 확인: member-service에는 고아 이벤트가 전혀 남지 않고(prepare 단계에서 이미 중단), 복구 후 정상 등록이 재개됨을 curl로 검증했다.
- **모델 체커 갱신**: 사가 정의가 이제 HTTP 포트(`UserProvisioningPort`/`MemberProvisioningPort`) 뒤에 있으므로, 모델 체커는 실제 서비스 대신 그 계약을 흉내 내는 fake 어댑터로 16경로를 검증한다 (`RegistrationSagaModelCheckTest`).
