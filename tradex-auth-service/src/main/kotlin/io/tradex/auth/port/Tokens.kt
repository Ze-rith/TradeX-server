package io.tradex.auth.port

import java.time.Instant

data class TokenSubject(val userId: String, val role: String)

data class IssuedToken(val value: String, val jti: String, val expiresAt: Instant)

data class TokenPair(val accessToken: IssuedToken, val refreshToken: IssuedToken)

data class VerifiedToken(val subject: TokenSubject, val jti: String, val expiresAt: Instant)

interface TokenIssuer {
    fun issue(subject: TokenSubject): TokenPair
}

interface TokenVerifier {
    fun verifyAccess(rawToken: String): VerifiedToken
    fun verifyRefresh(rawToken: String): VerifiedToken
}

interface PasswordHasher {
    fun hash(rawPassword: String): String
    fun matches(rawPassword: String, hash: String): Boolean
}
