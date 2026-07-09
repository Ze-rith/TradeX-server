package io.chronos.tradex.member.domain

import java.time.LocalDate
import java.time.Period

sealed class MemberException(message: String) : RuntimeException(message)
class InvalidNameException : MemberException("이름 형식이 올바르지 않습니다")
class InvalidBirthDateException : MemberException("생년월일이 올바르지 않습니다")
class UnderageException : MemberException("만 14세 이상만 가입할 수 있습니다")
class InvalidPhoneNumberException : MemberException("전화번호 형식이 올바르지 않습니다")
class PhoneNumberAlreadyExistsException : MemberException("이미 등록된 전화번호입니다")

/** 레거시 Name과 동일: 한글/영문 시작, 공백 정규화, 최대 50자. */
@JvmInline
value class Name private constructor(val value: String) {
    companion object {
        private val PATTERN = Regex("^[가-힣A-Za-z][가-힣A-Za-z\\s\\-]{0,49}$")
        private val WHITESPACE = Regex("\\s+")

        fun of(raw: String): Name {
            val normalized = raw.trim().replace(WHITESPACE, " ")
            if (normalized.isBlank() || !PATTERN.matches(normalized)) throw InvalidNameException()
            return Name(normalized)
        }
    }
}

/** 레거시 BirthDate와 동일: 1900-01-01 이후, 미래 불가, 만 14세 이상. */
@JvmInline
value class BirthDate private constructor(val value: LocalDate) {
    companion object {
        private val MIN_DATE: LocalDate = LocalDate.of(1900, 1, 1)
        private const val MIN_AGE = 14

        fun of(value: LocalDate, today: LocalDate): BirthDate {
            if (value.isBefore(MIN_DATE) || !value.isBefore(today)) throw InvalidBirthDateException()
            if (Period.between(value, today).years < MIN_AGE) throw UnderageException()
            return BirthDate(value)
        }
    }
}

/** 레거시 PhoneNumber와 동일: 한국 번호를 E.164(+82…)로 정규화. */
@JvmInline
value class PhoneNumber private constructor(val e164: String) {
    companion object {
        private val DIGITS = Regex("\\D")
        private val E164_KR_PATTERN = Regex("^\\+82(10|11|16|17|18|19|2|[3-6][1-5])\\d{6,8}$")
        private const val KR_PREFIX = "+82"

        fun of(raw: String): PhoneNumber {
            val normalized = normalizeKorean(raw)
            if (!E164_KR_PATTERN.matches(normalized)) throw InvalidPhoneNumberException()
            return PhoneNumber(normalized)
        }

        private fun normalizeKorean(raw: String): String {
            val withoutSpaces = raw.trim().replace(" ", "")
            if (withoutSpaces.isEmpty()) throw InvalidPhoneNumberException()

            if (withoutSpaces.startsWith(KR_PREFIX)) {
                return KR_PREFIX + withoutSpaces.substring(KR_PREFIX.length).replace(DIGITS, "")
            }
            val digitsOnly = withoutSpaces.replace(DIGITS, "")
            return when {
                digitsOnly.startsWith("82") -> KR_PREFIX + digitsOnly.substring(2)
                digitsOnly.startsWith("0") -> KR_PREFIX + digitsOnly.substring(1)
                else -> throw InvalidPhoneNumberException()
            }
        }
    }
}
