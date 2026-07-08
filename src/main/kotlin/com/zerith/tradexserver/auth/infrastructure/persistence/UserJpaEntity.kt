package com.zerith.tradexserver.auth.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "users",
    schema = "auth"
)
class UserJpaEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "email", nullable = false, unique = true, length = 255)
    val email: String,

    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String,

    @Column(name = "credential_updated_at", nullable = false)
    var credentialUpdatedAt: Instant,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: UserStatusJpa,

    @Column(name = "failure_count", nullable = false)
    var failureCount: Int,

    @Column(name = "last_failure_at")
    var lastFailureAt: Instant?,

    @Column(name = "locked_until")
    var lockedUntil: Instant?,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
)