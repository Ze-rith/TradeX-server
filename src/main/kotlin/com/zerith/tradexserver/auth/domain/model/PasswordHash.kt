package com.zerith.tradexserver.auth.domain.model

class PasswordHash private constructor(
    val value: String
) {
    companion object {
        fun fromHashed(hashed: String): PasswordHash {
            require(hashed.isNotBlank()) {
                "invalid hash"
            }
            return PasswordHash(hashed)
        }
    }

    override fun toString(): String {
        return "PasswordHash(masked)"
    }
}