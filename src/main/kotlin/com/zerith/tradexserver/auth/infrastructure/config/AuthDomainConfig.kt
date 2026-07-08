package com.zerith.tradexserver.auth.infrastructure.config

import com.zerith.tradexserver.auth.domain.policy.LockPolicy
import com.zerith.tradexserver.auth.domain.policy.PasswordPolicy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AuthDomainConfig {

    @Bean
    fun passwordPolicy(): PasswordPolicy {
        return PasswordPolicy()
    }

    @Bean
    fun lockPolicy(): LockPolicy {
        return LockPolicy.default()
    }
}