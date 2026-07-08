package com.zerith.tradexserver.auth.domain.model

import java.time.Instant

class Credential private constructor(
    val passwordHash: PasswordHash,
    val updatedAt: Instant
) {
    companion object {
        fun issue(
            passwordHash: PasswordHash,
            now: Instant
        ): Credential {
            return Credential(passwordHash, now)
        }
    }

    fun rotate(
        passwordHash: PasswordHash,
        now: Instant
    ): Credential {
        return Credential(passwordHash, now)
    }
}