package io.chronos.tradex.auth.domain

import io.chronos.core.aggregate.Aggregate
import io.chronos.tradex.auth.contract.AccessTokenBlacklisted
import io.chronos.tradex.auth.contract.AuthEvent
import io.chronos.tradex.auth.contract.RefreshTokenIssued
import io.chronos.tradex.auth.contract.RefreshTokenRevoked
import io.chronos.tradex.auth.contract.SignInFailed
import io.chronos.tradex.auth.contract.SignInSucceeded
import io.chronos.tradex.auth.contract.UserRegistered
import io.chronos.tradex.auth.contract.UserRegistrationRevoked
import java.time.Instant

/**
 * 레거시 User 엔티티(가변 상태 + JPA)의 이벤트소싱 대체.
 * 잠금 해제는 이벤트 없이 시간 파생: `lockedUntil < now`면 잠기지 않은 것이다.
 */
data class UserState(
    val registered: Boolean = false,
    val revoked: Boolean = false,
    val email: String? = null,
    val passwordHash: String? = null,
    val failureCount: Int = 0,
    val lockedUntil: Instant? = null,
    /** 단일 활성 refresh 세션 (레거시의 Redis userId→jti와 동일 정책). */
    val activeRefreshJti: String? = null,
    /** 사인아웃된 access jti → 만료 시각. */
    val blacklist: Map<String, Instant> = emptyMap(),
) {
    val exists: Boolean get() = registered && !revoked

    fun isLocked(now: Instant): Boolean = lockedUntil?.isAfter(now) == true

    fun isBlacklisted(jti: String, now: Instant): Boolean = blacklist[jti]?.isAfter(now) == true
}

object UserAggregate : Aggregate<UserState, AuthEvent> {
    override val type = "User"
    override val initial = UserState()

    override fun evolve(state: UserState, event: AuthEvent): UserState = when (event) {
        is UserRegistered -> state.copy(registered = true, email = event.email, passwordHash = event.passwordHash)
        is UserRegistrationRevoked -> state.copy(revoked = true, activeRefreshJti = null)
        is SignInFailed -> state.copy(failureCount = event.failureCount, lockedUntil = event.lockedUntil)
        is SignInSucceeded -> state.copy(failureCount = 0, lockedUntil = null)
        is RefreshTokenIssued -> state.copy(activeRefreshJti = event.jti)
        is RefreshTokenRevoked ->
            if (state.activeRefreshJti == event.jti) state.copy(activeRefreshJti = null) else state
        is AccessTokenBlacklisted -> state.copy(blacklist = state.blacklist + (event.jti to event.expiresAt))
    }
}
