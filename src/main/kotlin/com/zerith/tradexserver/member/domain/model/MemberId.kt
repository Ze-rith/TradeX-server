package com.zerith.tradexserver.member.domain.model

import java.util.UUID

class MemberId private constructor(
    val value: UUID
) {
    companion object {
        fun from(value: UUID): MemberId {
            return MemberId(value)
        }

        fun fromString(value: String): MemberId {
            return MemberId(UUID.fromString(value))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemberId) return false
        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    fun asString(): String {
        return value.toString()
    }
}