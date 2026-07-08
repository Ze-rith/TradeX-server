package com.zerith.tradexserver.member.domain.model

import com.zerith.tradexserver.member.domain.exception.InvalidPhoneNumberException

class PhoneNumber private constructor(
    val e164: String
) {
    companion object {
        private val DIGITS = Regex("\\D")
        private val E164_KR_PATTERN = Regex("^\\+82(10|11|16|17|18|19|2|[3-6][1-5])\\d{6,8}$")
        private const val KR_PREFIX = "+82"

        fun of(raw: String): PhoneNumber {
            val normalized = normalizeKorean(raw)
            if (!E164_KR_PATTERN.matches(normalized)) {
                throw InvalidPhoneNumberException()
            }
            return PhoneNumber(normalized)
        }

        fun restore(e164: String): PhoneNumber {
            return PhoneNumber(e164)
        }

        private fun normalizeKorean(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) {
                throw InvalidPhoneNumberException()
            }
            val withoutSpaces = trimmed.replace(" ", "")

            if (withoutSpaces.startsWith(KR_PREFIX)) {
                val rest = withoutSpaces.substring(KR_PREFIX.length).replace(DIGITS, "")
                return "$KR_PREFIX$rest"
            }

            val digitsOnly = withoutSpaces.replace(DIGITS, "")

            if (digitsOnly.startsWith("82")) {
                return "$KR_PREFIX${digitsOnly.substring(2)}"
            }

            if (digitsOnly.startsWith("0")) {
                return "$KR_PREFIX${digitsOnly.substring(1)}"
            }

            throw InvalidPhoneNumberException()
        }
    }

    override fun toString(): String {
        return "PhoneNumber(masked)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PhoneNumber) return false
        return e164 == other.e164
    }

    override fun hashCode(): Int {
        return e164.hashCode()
    }
}