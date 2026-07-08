package com.zerith.tradexserver.auth.domain.model

import java.time.Duration
import java.time.Instant

class LoginAttempt private constructor(
    val failureCount: Int,
    val lastFailureAt: Instant?,
    val lockedUntil: Instant?
) {
    companion object {
        fun initial(): LoginAttempt {
            return LoginAttempt(0, null, null)
        }

        fun restore(
            failureCount: Int,
            lastFailureAt: Instant?,
            lockedUntil: Instant?
        ): LoginAttempt {
            return LoginAttempt(failureCount, lastFailureAt, lockedUntil)
        }
    }

    fun isLocked(now: Instant): Boolean {
        val until = lockedUntil ?: return false
        return now.isBefore(until)
    }

    fun increaseFailure(
        now: Instant,
        threshold: Int,
        lockDuration: Duration
    ): LoginAttempt {
        val nextCount = failureCount + 1
        val nextLockedUntil = if (nextCount >= threshold) {
            now.plus(lockDuration)
        } else {
            lockedUntil
        }
        return LoginAttempt(nextCount, now, nextLockedUntil)
    }

    fun reset(): LoginAttempt {
        return LoginAttempt(0, null, null)
    }
}