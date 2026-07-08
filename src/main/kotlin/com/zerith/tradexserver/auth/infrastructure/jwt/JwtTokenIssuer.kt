package com.zerith.tradexserver.auth.infrastructure.jwt

import com.zerith.tradexserver.auth.domain.port.TokenIssuer
import com.zerith.tradexserver.auth.domain.token.AccessToken
import com.zerith.tradexserver.auth.domain.token.RefreshToken
import com.zerith.tradexserver.auth.domain.token.TokenPair
import com.zerith.tradexserver.auth.domain.token.TokenSubject
import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Component
import java.security.PrivateKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID

@Component
class JwtTokenIssuer(
    private val jwtProperties: JwtProperties,
    private val jwtKeyLoader: JwtKeyLoader,
    private val clock: Clock
) : TokenIssuer {

    private val privateKey: PrivateKey by lazy {
        jwtKeyLoader.loadPrivateKey()
    }

    override fun issue(subject: TokenSubject): TokenPair {
        val now = clock.instant()

        val access = buildAccess(subject, now)
        val refresh = buildRefresh(subject, now)

        return TokenPair.of(access, refresh)
    }

    private fun buildAccess(
        subject: TokenSubject,
        now: Instant
    ): AccessToken {
        val jti = UUID.randomUUID().toString()
        val expiresAt = now.plus(jwtProperties.accessTtl)
        val value = compact(
            subject = subject,
            jti = jti,
            now = now,
            expiresAt = expiresAt,
            type = JwtTokenType.ACCESS,
            ttl = jwtProperties.accessTtl
        )
        return AccessToken.of(value, jti, expiresAt)
    }

    private fun buildRefresh(
        subject: TokenSubject,
        now: Instant
    ): RefreshToken {
        val jti = UUID.randomUUID().toString()
        val expiresAt = now.plus(jwtProperties.refreshTtl)
        val value = compact(
            subject = subject,
            jti = jti,
            now = now,
            expiresAt = expiresAt,
            type = JwtTokenType.REFRESH,
            ttl = jwtProperties.refreshTtl
        )
        return RefreshToken.of(value, jti, expiresAt)
    }

    private fun compact(
        subject: TokenSubject,
        jti: String,
        now: Instant,
        expiresAt: Instant,
        type: JwtTokenType,
        ttl: Duration
    ): String {
        return Jwts.builder()
            .header()
            .keyId(jwtProperties.keyId)
            .and()
            .issuer(jwtProperties.issuer)
            .subject(subject.userId.asString())
            .id(jti)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .claim(CLAIM_ROLE, subject.role)
            .claim(CLAIM_TYPE, type.claim)
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact()
    }

    companion object {
        private const val CLAIM_ROLE = "role"
        private const val CLAIM_TYPE = "typ"
    }
}