package com.zerith.tradexserver.member.domain.model

class PhoneNumberHash private constructor(
    val value: String
) {
    companion object {
        fun fromHashed(hashed: String): PhoneNumberHash {
            require(hashed.isNotBlank()) {
                "invalid hash"
            }
            return PhoneNumberHash(hashed)
        }
    }

    override fun toString(): String {
        return "PhoneNumberHash(masked)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PhoneNumberHash) return false
        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}