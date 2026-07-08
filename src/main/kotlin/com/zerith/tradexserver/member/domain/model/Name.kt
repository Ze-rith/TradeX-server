package com.zerith.tradexserver.member.domain.model

import com.zerith.tradexserver.member.domain.exception.InvalidNameException

class Name private constructor(
    val value: String
) {
    companion object {
        private val PATTERN = Regex("^[가-힣A-Za-z][가-힣A-Za-z\\s\\-]{0,49}$")

        fun of(raw: String): Name {
            val normalized = raw.trim().replace(WHITESPACE, " ")
            if (normalized.isBlank()) {
                throw InvalidNameException()
            }
            if (!PATTERN.matches(normalized)) {
                throw InvalidNameException()
            }
            return Name(normalized)
        }

        private val WHITESPACE = Regex("\\s+")
    }

    override fun toString(): String {
        return "Name(masked)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Name) return false
        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}