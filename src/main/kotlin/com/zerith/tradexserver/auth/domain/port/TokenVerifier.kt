package com.zerith.tradexserver.auth.domain.port

import com.zerith.tradexserver.auth.domain.token.TokenSubject
import java.time.Instant

interface TokenVerifier {

    fun verifyAccess(rawToken: String): VerifiedAccess

    fun verifyRefresh(rawToken: String): VerifiedRefresh
}

class VerifiedAccess(
    val subject: TokenSubject,
    val jti: String,
    val expiresAt: Instant
)

class VerifiedRefresh(
    val subject: TokenSubject,
    val jti: String,
    val expiresAt: Instant
)