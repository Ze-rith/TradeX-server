package com.zerith.tradexserver.auth.infrastructure.crypto

import com.zerith.tradexserver.auth.domain.model.PasswordHash
import com.zerith.tradexserver.auth.domain.port.PasswordHasher
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

@Component
class BcryptPasswordHasher(
    private val encoder: BCryptPasswordEncoder
) : PasswordHasher {

    override fun hash(rawPassword: String): PasswordHash {
        val hashed = encoder.encode(rawPassword)
        return PasswordHash.fromHashed(hashed)
    }

    override fun matches(
        rawPassword: String,
        passwordHash: PasswordHash
    ): Boolean {
        return encoder.matches(rawPassword, passwordHash.value)
    }
}