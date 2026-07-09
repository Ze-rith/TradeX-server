package io.tradex.auth.domain

import io.tradex.core.aggregate.Aggregate
import io.tradex.auth.contract.AccessTokenBlacklisted
import io.tradex.auth.contract.AuthEvent
import io.tradex.auth.contract.RefreshTokenIssued
import io.tradex.auth.contract.RefreshTokenRevoked
import io.tradex.auth.contract.SignInFailed
import io.tradex.auth.contract.SignInSucceeded
import io.tradex.auth.contract.UserRegistered
import io.tradex.auth.contract.UserRegistrationRevoked
import java.time.Instant

data class UserState(
    val registered: Boolean = false,
    val revoked: Boolean = false,
    val email: String? = null,
    val passwordHash: String? = null,
    val failureCount: Int = 0,
    val lockedUntil: Instant? = null,

    val activeRefreshJti: String? = null,

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
