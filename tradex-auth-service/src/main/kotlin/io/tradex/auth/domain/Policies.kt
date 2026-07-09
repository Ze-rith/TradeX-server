package io.tradex.auth.domain

import java.time.Duration

/** 레거시와 동일한 도메인 예외 계층 (HTTP 매핑은 api 계층에서). */
sealed class AuthException(message: String) : RuntimeException(message)
class InvalidEmailException : AuthException("이메일 형식이 올바르지 않습니다")
class WeakPasswordException : AuthException("비밀번호가 정책을 충족하지 않습니다")
class EmailAlreadyExistsException : AuthException("이미 등록된 이메일입니다")
class InvalidCredentialException : AuthException("이메일 또는 비밀번호가 올바르지 않습니다")
class AccountLockedException : AuthException("계정이 잠겨 있습니다")
class TokenInvalidException : AuthException("토큰이 유효하지 않습니다")

/** 정규화된 이메일 (trim + lowercase). 레거시 Email과 동일 규칙. */
@JvmInline
value class Email private constructor(val value: String) {
    companion object {
        private val PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        fun of(raw: String): Email {
            val normalized = raw.trim().lowercase()
            if (!PATTERN.matches(normalized)) throw InvalidEmailException()
            return Email(normalized)
        }
    }

    fun localPart(): String = value.substringBefore('@')
}

/** 레거시와 동일: 12자 이상, 문자 종류 3종 이상, 이메일 로컬파트 포함 금지. */
object PasswordPolicy {
    private const val MIN_LENGTH = 12
    private const val REQUIRED_CATEGORIES = 3

    fun validate(rawPassword: String, email: Email) {
        if (rawPassword.length < MIN_LENGTH) throw WeakPasswordException()
        val categories = listOf(
            rawPassword.any { it.isLowerCase() },
            rawPassword.any { it.isUpperCase() },
            rawPassword.any { it.isDigit() },
            rawPassword.any { !it.isLetterOrDigit() },
        ).count { it }
        if (categories < REQUIRED_CATEGORIES) throw WeakPasswordException()
        if (rawPassword.lowercase().contains(email.localPart().lowercase())) throw WeakPasswordException()
    }
}

/** 레거시와 동일: 5회 실패 시 30분 잠금. */
data class LockPolicy(
    val failureThreshold: Int = 5,
    val lockDuration: Duration = Duration.ofMinutes(30),
)
