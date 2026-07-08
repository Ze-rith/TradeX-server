package com.zerith.tradexserver.member.infrastructure.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(MemberCryptoProperties::class)
class MemberCryptoConfig