package com.zerith.tradexserver.auth.application

import com.zerith.tradexserver.auth.application.command.ReissueTokenCommand
import com.zerith.tradexserver.auth.domain.exception.TokenInvalidException
import com.zerith.tradexserver.auth.domain.port.TokenIssuer
import com.zerith.tradexserver.auth.domain.port.TokenVerifier
import com.zerith.tradexserver.auth.domain.repository.RefreshTokenStore
import com.zerith.tradexserver.auth.domain.token.TokenPair
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

@Component
class ReissueTokenUseCase(
    private val tokenVerifier: TokenVerifier,
    private val tokenIssuer: TokenIssuer,
    private val refreshTokenStore: RefreshTokenStore,
    private val clock: Clock
) {

    @Transactional
    fun execute(command: ReissueTokenCommand): TokenPair {
        val verified = tokenVerifier.verifyRefresh(command.rawRefreshToken)

        val storedJti = refreshTokenStore.findJtiByUserId(verified.subject.userId)
            ?: throw TokenInvalidException()

        if (storedJti != verified.jti) {
            refreshTokenStore.delete(verified.subject.userId)
            throw TokenInvalidException()
        }

        if (refreshTokenStore.isBlacklisted(verified.jti)) {
            throw TokenInvalidException()
        }

        refreshTokenStore.delete(verified.subject.userId)

        val tokenPair = tokenIssuer.issue(verified.subject)
        val now = clock.instant()
        val refreshTtl = Duration.between(now, tokenPair.refreshToken.expiresAt)

        refreshTokenStore.save(
            userId = verified.subject.userId,
            jti = tokenPair.refreshToken.jti,
            ttl = refreshTtl
        )

        return tokenPair
    }
}