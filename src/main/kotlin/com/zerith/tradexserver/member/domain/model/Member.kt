package com.zerith.tradexserver.member.domain.model

import java.time.Instant

class Member private constructor(
    val id: MemberId,
    name: Name,
    birthDate: BirthDate,
    phoneNumber: PhoneNumber,
    phoneNumberHash: PhoneNumberHash,
    val createdAt: Instant,
    updatedAt: Instant
) {
    var name: Name = name
        private set

    var birthDate: BirthDate = birthDate
        private set

    var phoneNumber: PhoneNumber = phoneNumber
        private set

    var phoneNumberHash: PhoneNumberHash = phoneNumberHash
        private set

    var updatedAt: Instant = updatedAt
        private set

    companion object {
        fun create(
            id: MemberId,
            name: Name,
            birthDate: BirthDate,
            phoneNumber: PhoneNumber,
            phoneNumberHash: PhoneNumberHash,
            now: Instant
        ): Member {
            return Member(
                id = id,
                name = name,
                birthDate = birthDate,
                phoneNumber = phoneNumber,
                phoneNumberHash = phoneNumberHash,
                createdAt = now,
                updatedAt = now
            )
        }

        fun restore(
            id: MemberId,
            name: Name,
            birthDate: BirthDate,
            phoneNumber: PhoneNumber,
            phoneNumberHash: PhoneNumberHash,
            createdAt: Instant,
            updatedAt: Instant
        ): Member {
            return Member(
                id = id,
                name = name,
                birthDate = birthDate,
                phoneNumber = phoneNumber,
                phoneNumberHash = phoneNumberHash,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }

    fun changePhoneNumber(
        phoneNumber: PhoneNumber,
        phoneNumberHash: PhoneNumberHash,
        now: Instant
    ) {
        this.phoneNumber = phoneNumber
        this.phoneNumberHash = phoneNumberHash
        this.updatedAt = now
    }

    fun changeName(
        name: Name,
        now: Instant
    ) {
        this.name = name
        this.updatedAt = now
    }
}