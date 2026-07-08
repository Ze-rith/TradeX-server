package com.zerith.tradexserver.auth.domain.token

import java.time.Instant

class RefreshToken private constructor(
    val value: String,
    val jti: String,
    val expiresAt: Instant
) {
    companion object {
        fun of(
            value: String,
            jti: String,
            expiresAt: Instant
        ): RefreshToken {
            return RefreshToken(value, jti, expiresAt)
        }
    }

    override fun toString(): String {
        return "RefreshToken(masked)"
    }
}