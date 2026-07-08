package com.zerith.tradexserver.auth.infrastructure.jwt

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "tradex.auth.jwt")
class JwtProperties(
    val issuer: String,
    val accessTtl: Duration,
    val refreshTtl: Duration,
    val keyId: String,
    val privateKeyPem: String,
    val publicKeyPem: String
)