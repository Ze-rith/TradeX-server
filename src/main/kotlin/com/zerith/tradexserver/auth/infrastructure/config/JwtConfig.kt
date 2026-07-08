package com.zerith.tradexserver.auth.infrastructure.config

import com.zerith.tradexserver.auth.infrastructure.jwt.JwtProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(JwtProperties::class)
class JwtConfig