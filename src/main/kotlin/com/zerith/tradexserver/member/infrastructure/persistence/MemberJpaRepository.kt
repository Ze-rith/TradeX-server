package com.zerith.tradexserver.member.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MemberJpaRepository : JpaRepository<MemberJpaEntity, UUID> {

    fun existsByPhoneNumberHash(phoneNumberHash: String): Boolean
}