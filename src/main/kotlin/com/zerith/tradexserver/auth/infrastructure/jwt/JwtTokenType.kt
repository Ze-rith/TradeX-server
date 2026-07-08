package com.zerith.tradexserver.auth.infrastructure.jwt

enum class JwtTokenType(
    val claim: String
) {
    ACCESS("access"),
    REFRESH("refresh")
}