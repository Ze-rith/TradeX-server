package com.zerith.tradexserver.member.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class MemberDomainConfig {

    @Bean
    fun memberClock(): Clock {
        return Clock.systemUTC()
    }
}