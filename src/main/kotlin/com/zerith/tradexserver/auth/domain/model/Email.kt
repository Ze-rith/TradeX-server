package com.zerith.tradexserver.auth.domain.model

import com.zerith.tradexserver.auth.domain.exception.InvalidEmailException

class Email private constructor(
    val value: String
) {
    companion object {
        private val PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        fun of(raw: String): Email {
            val normalized = raw.trim().lowercase()
            if (!PATTERN.matches(normalized)) {
                throw InvalidEmailException()
            }
            return Email(normalized)
        }
    }

    fun localPart(): String {
        return value.substringBefore('@')
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Email) return false
        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}