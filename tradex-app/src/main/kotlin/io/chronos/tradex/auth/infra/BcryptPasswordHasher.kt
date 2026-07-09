package io.chronos.tradex.auth.infra

import io.chronos.tradex.auth.port.PasswordHasher
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class BcryptPasswordHasher : PasswordHasher {
    private val encoder = BCryptPasswordEncoder()

    override fun hash(rawPassword: String): String = encoder.encode(rawPassword)

    override fun matches(rawPassword: String, hash: String): Boolean = encoder.matches(rawPassword, hash)
}
