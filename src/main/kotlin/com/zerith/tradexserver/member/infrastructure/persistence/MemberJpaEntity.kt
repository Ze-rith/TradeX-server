package com.zerith.tradexserver.member.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "members",
    schema = "member"
)
class MemberJpaEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "name_ciphertext", nullable = false, columnDefinition = "TEXT")
    var nameCiphertext: String,

    @Column(name = "name_key_version", nullable = false)
    var nameKeyVersion: Int,

    @Column(name = "birth_date_ciphertext", nullable = false, columnDefinition = "TEXT")
    var birthDateCiphertext: String,

    @Column(name = "birth_date_key_version", nullable = false)
    var birthDateKeyVersion: Int,

    @Column(name = "phone_number_ciphertext", nullable = false, columnDefinition = "TEXT")
    var phoneNumberCiphertext: String,

    @Column(name = "phone_number_key_version", nullable = false)
    var phoneNumberKeyVersion: Int,

    @Column(name = "phone_number_hash", nullable = false, unique = true, length = 64)
    var phoneNumberHash: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
)