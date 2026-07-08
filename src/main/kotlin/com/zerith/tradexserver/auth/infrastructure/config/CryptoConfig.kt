package com.zerith.tradexserver.auth.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@Configuration
class CryptoConfig {

    @Bean
    fun bcryptPasswordEncoder(): BCryptPasswordEncoder {
        return BCryptPasswordEncoder(BCRYPT_STRENGTH)
    }

    companion object {
        private const val BCRYPT_STRENGTH = 12
    }
}