package io.tradex.registration.port

import java.time.LocalDate

data class PreparedCredential(val email: String, val passwordHash: String)

data class PreparedMember(
    val encryptedName: String,
    val encryptedBirthDate: String,
    val encryptedPhoneNumber: String,
    val phoneNumberHash: String,
)

/**
 * 다운스트림 서비스가 요청을 명시적으로 거절한 경우 (4xx).
 * code/message는 해당 서비스의 응답을 그대로 실어 나른다 — 사가 step에서는 영구 실패로 취급된다.
 */
class ProvisioningRejectedException(
    val status: Int,
    val code: String,
    override val message: String,
) : RuntimeException("[$code] $message")

/** auth-service 내부 API 포트. register/revoke는 멱등이어야 한다 (사가 재시도·보상). */
interface UserProvisioningPort {
    fun prepareCredential(email: String, rawPassword: String): PreparedCredential
    fun registerUser(userId: String, email: String, passwordHash: String)
    fun revokeUser(userId: String, reason: String)
}

/** member-service 내부 API 포트. */
interface MemberProvisioningPort {
    fun prepareMember(name: String, birthDate: LocalDate, phoneNumber: String): PreparedMember
    fun createMember(memberId: String, member: PreparedMember)
    fun revokeMember(memberId: String, reason: String)
}
