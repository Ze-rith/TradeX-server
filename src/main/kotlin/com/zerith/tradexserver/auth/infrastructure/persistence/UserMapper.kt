package com.zerith.tradexserver.auth.infrastructure.persistence

import com.zerith.tradexserver.auth.domain.model.Credential
import com.zerith.tradexserver.auth.domain.model.Email
import com.zerith.tradexserver.auth.domain.model.LoginAttempt
import com.zerith.tradexserver.auth.domain.model.PasswordHash
import com.zerith.tradexserver.auth.domain.model.User
import com.zerith.tradexserver.auth.domain.model.UserId
import com.zerith.tradexserver.auth.domain.model.UserStatus

object UserMapper {

    fun toDomain(entity: UserJpaEntity): User {
        return User.restore(
            id = UserId.from(entity.id),
            email = Email.of(entity.email),
            credential = Credential.issue(
                passwordHash = PasswordHash.fromHashed(entity.passwordHash),
                now = entity.credentialUpdatedAt
            ),
            status = toDomainStatus(entity.status),
            loginAttempt = LoginAttempt.restore(
                failureCount = entity.failureCount,
                lastFailureAt = entity.lastFailureAt,
                lockedUntil = entity.lockedUntil
            ),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    fun toEntity(user: User): UserJpaEntity {
        return UserJpaEntity(
            id = user.id.value,
            email = user.email.value,
            passwordHash = user.credential.passwordHash.value,
            credentialUpdatedAt = user.credential.updatedAt,
            status = toJpaStatus(user.status),
            failureCount = user.loginAttempt.failureCount,
            lastFailureAt = user.loginAttempt.lastFailureAt,
            lockedUntil = user.loginAttempt.lockedUntil,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt
        )
    }

    fun applyTo(
        entity: UserJpaEntity,
        user: User
    ) {
        entity.passwordHash = user.credential.passwordHash.value
        entity.credentialUpdatedAt = user.credential.updatedAt
        entity.status = toJpaStatus(user.status)
        entity.failureCount = user.loginAttempt.failureCount
        entity.lastFailureAt = user.loginAttempt.lastFailureAt
        entity.lockedUntil = user.loginAttempt.lockedUntil
        entity.updatedAt = user.updatedAt
    }

    private fun toDomainStatus(status: UserStatusJpa): UserStatus {
        return when (status) {
            UserStatusJpa.ACTIVE -> UserStatus.ACTIVE
            UserStatusJpa.LOCKED -> UserStatus.LOCKED
            UserStatusJpa.DEACTIVATED -> UserStatus.DEACTIVATED
        }
    }

    private fun toJpaStatus(status: UserStatus): UserStatusJpa {
        return when (status) {
            UserStatus.ACTIVE -> UserStatusJpa.ACTIVE
            UserStatus.LOCKED -> UserStatusJpa.LOCKED
            UserStatus.DEACTIVATED -> UserStatusJpa.DEACTIVATED
        }
    }
}