package com.zerith.tradexserver.auth.domain.model

import com.zerith.tradexserver.auth.domain.exception.AccountLockedException
import com.zerith.tradexserver.auth.domain.policy.LockPolicy
import java.time.Instant

class User private constructor(
    val id: UserId,
    val email: Email,
    credential: Credential,
    status: UserStatus,
    loginAttempt: LoginAttempt,
    val createdAt: Instant,
    updatedAt: Instant
) {
    var credential: Credential = credential
        private set

    var status: UserStatus = status
        private set

    var loginAttempt: LoginAttempt = loginAttempt
        private set

    var updatedAt: Instant = updatedAt
        private set

    companion object {
        fun register(
            email: Email,
            credential: Credential,
            now: Instant
        ): User {
            return User(
                id = UserId.newId(),
                email = email,
                credential = credential,
                status = UserStatus.ACTIVE,
                loginAttempt = LoginAttempt.initial(),
                createdAt = now,
                updatedAt = now
            )
        }

        fun restore(
            id: UserId,
            email: Email,
            credential: Credential,
            status: UserStatus,
            loginAttempt: LoginAttempt,
            createdAt: Instant,
            updatedAt: Instant
        ): User {
            return User(
                id = id,
                email = email,
                credential = credential,
                status = status,
                loginAttempt = loginAttempt,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }

    fun assertNotLocked(now: Instant) {
        if (status == UserStatus.LOCKED && loginAttempt.isLocked(now)) {
            throw AccountLockedException()
        }
        if (status == UserStatus.LOCKED && !loginAttempt.isLocked(now)) {
            status = UserStatus.ACTIVE
            loginAttempt = loginAttempt.reset()
        }
    }

    fun recordSignInFailure(
        now: Instant,
        policy: LockPolicy
    ) {
        loginAttempt = loginAttempt.increaseFailure(
            now = now,
            threshold = policy.failureThreshold,
            lockDuration = policy.lockDuration
        )
        if (loginAttempt.isLocked(now)) {
            status = UserStatus.LOCKED
        }
        updatedAt = now
    }

    fun recordSignInSuccess(now: Instant) {
        loginAttempt = loginAttempt.reset()
        status = UserStatus.ACTIVE
        updatedAt = now
    }

    fun changePassword(
        passwordHash: PasswordHash,
        now: Instant
    ) {
        credential = credential.rotate(passwordHash, now)
        updatedAt = now
    }
}