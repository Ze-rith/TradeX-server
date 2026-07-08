package com.zerith.tradexserver.auth.infrastructure.persistence

import com.zerith.tradexserver.auth.domain.model.Email
import com.zerith.tradexserver.auth.domain.model.User
import com.zerith.tradexserver.auth.domain.model.UserId
import com.zerith.tradexserver.auth.domain.repository.UserRepository
import org.springframework.stereotype.Component

@Component
class UserRepositoryAdapter(
    private val userJpaRepository: UserJpaRepository
) : UserRepository {

    override fun existsByEmail(email: Email): Boolean {
        return userJpaRepository.existsByEmail(email.value)
    }

    override fun findByEmail(email: Email): User? {
        val entity = userJpaRepository.findByEmail(email.value) ?: return null
        return UserMapper.toDomain(entity)
    }

    override fun findById(id: UserId): User? {
        val entity = userJpaRepository.findById(id.value).orElse(null) ?: return null
        return UserMapper.toDomain(entity)
    }

    override fun save(user: User): User {
        val existing = userJpaRepository.findById(user.id.value).orElse(null)
        val saved = if (existing == null) {
            userJpaRepository.save(UserMapper.toEntity(user))
        } else {
            UserMapper.applyTo(existing, user)
            userJpaRepository.save(existing)
        }
        return UserMapper.toDomain(saved)
    }
}