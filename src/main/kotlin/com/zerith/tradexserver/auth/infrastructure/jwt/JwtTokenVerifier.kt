package com.zerith.tradexserver.auth.infrastructure.jwt

import com.zerith.tradexserver.auth.domain.exception.TokenInvalidException
import com.zerith.tradexserver.auth.domain.model.UserId
import com.zerith.tradexserver.auth.domain.port.TokenVerifier
import com.zerith.tradexserver.auth.domain.port.VerifiedAccess
import com.zerith.tradexserver.auth.domain.port.VerifiedRefresh
import com.zerith.tradexserver.auth.domain.token.TokenSubject
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Component
import java.security.PublicKey

@Component
class JwtTokenVerifier(
    private val jwtProperties: JwtProperties,
    private val jwtKeyLoader: JwtKeyLoader
) : TokenVerifier {

    private val publicKey: PublicKey by lazy {
        jwtKeyLoader.loadPublicKey()
    }

    override fun verifyAccess(rawToken: String): VerifiedAccess {
        val claims = parse(rawToken)
        assertType(claims, JwtTokenType.ACCESS)
        val subject = toSubject(claims)
        val jti = claims.id ?: throw TokenInvalidException()
        val expiresAt = claims.expiration?.toInstant() ?: throw TokenInvalidException()
        return VerifiedAccess(subject, jti, expiresAt)
    }

    override fun verifyRefresh(rawToken: String): VerifiedRefresh {
        val claims = parse(rawToken)
        assertType(claims, JwtTokenType.REFRESH)
        val subject = toSubject(claims)
        val jti = claims.id ?: throw TokenInvalidException()
        val expiresAt = claims.expiration?.toInstant() ?: throw TokenInvalidException()
        return VerifiedRefresh(subject, jti, expiresAt)
    }

    private fun parse(rawToken: String): Claims {
        try {
            return Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(jwtProperties.issuer)
                .build()
                .parseSignedClaims(rawToken)
                .payload
        } catch (e: JwtException) {
            throw TokenInvalidException()
        } catch (e: IllegalArgumentException) {
            throw TokenInvalidException()
        }
    }

    private fun assertType(
        claims: Claims,
        expected: JwtTokenType
    ) {
        val actual = claims[CLAIM_TYPE] as? String ?: throw TokenInvalidException()
        if (actual != expected.claim) {
            throw TokenInvalidException()
        }
    }

    private fun toSubject(claims: Claims): TokenSubject {
        val subject = claims.subject ?: throw TokenInvalidException()
        val role = claims[CLAIM_ROLE] as? String ?: throw TokenInvalidException()
        val userId = try {
            UserId.fromString(subject)
        } catch (e: IllegalArgumentException) {
            throw TokenInvalidException()
        }
        return TokenSubject.of(userId, role)
    }

    companion object {
        private const val CLAIM_ROLE = "role"
        private const val CLAIM_TYPE = "typ"
    }
}