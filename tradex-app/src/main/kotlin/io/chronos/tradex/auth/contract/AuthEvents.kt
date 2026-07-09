package io.chronos.tradex.auth.contract

import io.chronos.core.event.AggregateId
import io.chronos.core.event.DomainEvent
import io.chronos.core.event.EventId
import io.chronos.membrane.EventSchema
import java.time.Instant

/**
 * 인증 Bounded Context의 이벤트 계약. aggregateId = userId.
 *
 * 레거시의 Redis refresh 스토어는 이 스트림의 토큰 이벤트로 대체된다 (DECISIONS.md D18):
 * 단일 활성 refresh jti, 회전 = Revoked+Issued 배치, 블랙리스트 = 상태 맵 파생.
 */
sealed interface AuthEvent : DomainEvent

@EventSchema(type = "UserRegistered", version = 1)
data class UserRegistered(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val email: String,
    /** bcrypt 해시. 평문 비밀번호는 어떤 이벤트에도 실리지 않는다. */
    val passwordHash: String,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : AuthEvent

/** 등록 사가의 보상: 멤버 생성 실패 시 계정을 소급 폐기한다. */
@EventSchema(type = "UserRegistrationRevoked", version = 1)
data class UserRegistrationRevoked(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val reason: String,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : AuthEvent

/**
 * 로그인 실패. 잠금 여부는 서비스가 LockPolicy로 **결정**해 여기 기록하고
 * evolve는 반영만 한다 (DECISIONS.md D19).
 */
@EventSchema(type = "SignInFailed", version = 1)
data class SignInFailed(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val failureCount: Int,
    val lockedUntil: Instant?,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : AuthEvent

@EventSchema(type = "SignInSucceeded", version = 1)
data class SignInSucceeded(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : AuthEvent

@EventSchema(type = "RefreshTokenIssued", version = 1)
data class RefreshTokenIssued(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val jti: String,
    val expiresAt: Instant,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : AuthEvent

@EventSchema(type = "RefreshTokenRevoked", version = 1)
data class RefreshTokenRevoked(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val jti: String,
    val reason: String,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : AuthEvent

/** 사인아웃된 access 토큰의 남은 수명 동안의 차단 목록 등재. */
@EventSchema(type = "AccessTokenBlacklisted", version = 1)
data class AccessTokenBlacklisted(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val jti: String,
    val expiresAt: Instant,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : AuthEvent
