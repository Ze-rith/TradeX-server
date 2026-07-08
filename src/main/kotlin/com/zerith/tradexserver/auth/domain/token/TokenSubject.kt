package com.zerith.tradexserver.auth.domain.token

import com.zerith.tradexserver.auth.domain.model.UserId

class TokenSubject private constructor(
    val userId: UserId,
    val role: String
) {
    companion object {
        fun of(
            userId: UserId,
            role: String
        ): TokenSubject {
            require(role.isNotBlank()) {
                "invalid role"
            }
            return TokenSubject(userId, role)
        }
    }
}