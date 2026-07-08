package com.zerith.tradexserver.member.infrastructure.persistence

import com.zerith.tradexserver.member.domain.model.BirthDate
import com.zerith.tradexserver.member.domain.model.EncryptedPii
import com.zerith.tradexserver.member.domain.model.Member
import com.zerith.tradexserver.member.domain.model.MemberId
import com.zerith.tradexserver.member.domain.model.Name
import com.zerith.tradexserver.member.domain.model.PhoneNumber
import com.zerith.tradexserver.member.domain.model.PhoneNumberHash
import com.zerith.tradexserver.member.domain.port.PiiCipher
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class MemberMapper(
    private val piiCipher: PiiCipher
) {

    fun toDomain(entity: MemberJpaEntity): Member {
        val name = Name.of(
            piiCipher.decrypt(
                EncryptedPii.of(entity.nameCiphertext, entity.nameKeyVersion)
            )
        )
        val birthDate = BirthDate.restore(
            LocalDate.parse(
                piiCipher.decrypt(
                    EncryptedPii.of(entity.birthDateCiphertext, entity.birthDateKeyVersion)
                )
            )
        )
        val phoneNumber = PhoneNumber.restore(
            piiCipher.decrypt(
                EncryptedPii.of(entity.phoneNumberCiphertext, entity.phoneNumberKeyVersion)
            )
        )
        val phoneNumberHash = PhoneNumberHash.fromHashed(entity.phoneNumberHash)

        return Member.restore(
            id = MemberId.from(entity.id),
            name = name,
            birthDate = birthDate,
            phoneNumber = phoneNumber,
            phoneNumberHash = phoneNumberHash,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    fun toEntity(member: Member): MemberJpaEntity {
        val nameEncrypted = piiCipher.encrypt(member.name.value)
        val birthDateEncrypted = piiCipher.encrypt(member.birthDate.value.toString())
        val phoneNumberEncrypted = piiCipher.encrypt(member.phoneNumber.e164)

        return MemberJpaEntity(
            id = member.id.value,
            nameCiphertext = nameEncrypted.ciphertext,
            nameKeyVersion = nameEncrypted.keyVersion,
            birthDateCiphertext = birthDateEncrypted.ciphertext,
            birthDateKeyVersion = birthDateEncrypted.keyVersion,
            phoneNumberCiphertext = phoneNumberEncrypted.ciphertext,
            phoneNumberKeyVersion = phoneNumberEncrypted.keyVersion,
            phoneNumberHash = member.phoneNumberHash.value,
            createdAt = member.createdAt,
            updatedAt = member.updatedAt
        )
    }

    fun applyTo(
        entity: MemberJpaEntity,
        member: Member
    ) {
        val nameEncrypted = piiCipher.encrypt(member.name.value)
        val birthDateEncrypted = piiCipher.encrypt(member.birthDate.value.toString())
        val phoneNumberEncrypted = piiCipher.encrypt(member.phoneNumber.e164)

        entity.nameCiphertext = nameEncrypted.ciphertext
        entity.nameKeyVersion = nameEncrypted.keyVersion
        entity.birthDateCiphertext = birthDateEncrypted.ciphertext
        entity.birthDateKeyVersion = birthDateEncrypted.keyVersion
        entity.phoneNumberCiphertext = phoneNumberEncrypted.ciphertext
        entity.phoneNumberKeyVersion = phoneNumberEncrypted.keyVersion
        entity.phoneNumberHash = member.phoneNumberHash.value
        entity.updatedAt = member.updatedAt
    }
}