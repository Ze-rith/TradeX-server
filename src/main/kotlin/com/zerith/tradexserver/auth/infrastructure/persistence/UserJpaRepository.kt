package com.zerith.tradexserver.auth.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserJpaRepository : JpaRepository<UserJpaEntity, UUID> {

    fun existsByEmail(email: String): Boolean

    fun findByEmail(email: String): UserJpaEntity?
}