package com.zerith.tradexserver.member.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "tradex.member.crypto")
class MemberCryptoProperties(
    val activeKeyVersion: Int,
    val aesKeys: Map<Int, String>,
    val hmacKey: String
)