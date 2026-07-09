package io.tradex.registration.port

import java.time.LocalDate

data class PreparedCredential(val email: String, val passwordHash: String)

data class PreparedMember(
    val encryptedName: String,
    val encryptedBirthDate: String,
    val encryptedPhoneNumber: String,
    val phoneNumberHash: String,
)

class ProvisioningRejectedException(
    val status: Int,
    val code: String,
    override val message: String,
) : RuntimeException("[$code] $message")

interface UserProvisioningPort {
    fun prepareCredential(email: String, rawPassword: String): PreparedCredential
    fun registerUser(userId: String, email: String, passwordHash: String)
    fun revokeUser(userId: String, reason: String)
}

interface MemberProvisioningPort {
    fun prepareMember(name: String, birthDate: LocalDate, phoneNumber: String): PreparedMember
    fun createMember(memberId: String, member: PreparedMember)
    fun revokeMember(memberId: String, reason: String)
}
