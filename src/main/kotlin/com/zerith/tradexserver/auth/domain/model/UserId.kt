package com.zerith.tradexserver.auth.domain.model

import java.util.UUID

class UserId private constructor(
    val value: UUID
) {
    companion object {
        fun newId(): UserId {
            return UserId(UUID.randomUUID())
        }

        fun from(value: UUID): UserId {
            return UserId(value)
        }

        fun fromString(value: String): UserId {
            return UserId(UUID.fromString(value))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserId) return false
        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    fun asString(): String {
        return value.toString()
    }
}