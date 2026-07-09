package io.tradex.registration

import io.tradex.registration.port.MemberProvisioningPort
import io.tradex.registration.port.PreparedCredential
import io.tradex.registration.port.PreparedMember
import io.tradex.registration.port.ProvisioningRejectedException
import io.tradex.registration.port.UserProvisioningPort
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

class FakeUserPort : UserProvisioningPort {
    val registered = ConcurrentHashMap.newKeySet<String>()
    val revoked = ConcurrentHashMap.newKeySet<String>()
    private val emails = ConcurrentHashMap<String, String>()

    fun userExists(userId: String): Boolean = userId in registered && userId !in revoked

    override fun prepareCredential(email: String, rawPassword: String): PreparedCredential {
        if (emails.containsKey(email)) throw ProvisioningRejectedException(409, "EMAIL_DUPLICATE", "이미 등록된 이메일입니다")
        return PreparedCredential(email, "bcrypt:$rawPassword")
    }

    override fun registerUser(userId: String, email: String, passwordHash: String) {
        if (userId in registered) return
        emails[email]?.let { if (it != userId) throw ProvisioningRejectedException(409, "EMAIL_DUPLICATE", "이미 등록된 이메일입니다") }
        registered += userId
        emails[email] = userId
    }

    override fun revokeUser(userId: String, reason: String) {
        if (userId !in registered || userId in revoked) return
        revoked += userId
        emails.entries.removeIf { it.value == userId }
    }
}

class FakeMemberPort : MemberProvisioningPort {
    val created = ConcurrentHashMap.newKeySet<String>()
    val revoked = ConcurrentHashMap.newKeySet<String>()
    private val phones = ConcurrentHashMap<String, String>()

    fun memberExists(memberId: String): Boolean = memberId in created && memberId !in revoked

    override fun prepareMember(name: String, birthDate: LocalDate, phoneNumber: String): PreparedMember {
        val hash = "hmac:$phoneNumber"
        if (phones.containsKey(hash)) throw ProvisioningRejectedException(409, "PHONE_DUPLICATE", "이미 등록된 전화번호입니다")
        return PreparedMember("enc:$name", "enc:$birthDate", "enc:$phoneNumber", hash)
    }

    override fun createMember(memberId: String, member: PreparedMember) {
        if (memberId in created) return
        phones[member.phoneNumberHash]?.let {
            if (it != memberId) throw ProvisioningRejectedException(409, "PHONE_DUPLICATE", "이미 등록된 전화번호입니다")
        }
        created += memberId
        phones[member.phoneNumberHash] = memberId
    }

    override fun revokeMember(memberId: String, reason: String) {
        if (memberId !in created || memberId in revoked) return
        revoked += memberId
        phones.entries.removeIf { it.value == memberId }
    }
}
