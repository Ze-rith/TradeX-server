package com.zerith.tradexserver.auth.application

import com.zerith.tradexserver.auth.application.command.SignOutCommand
import com.zerith.tradexserver.auth.domain.exception.TokenInvalidException
import com.zerith.tradexserver.auth.domain.port.TokenVerifier
import com.zerith.tradexserver.auth.domain.repository.RefreshTokenStore
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

@Component
class SignOutUseCase(
    private val tokenVerifier: TokenVerifier,
    private val refreshTokenStore: RefreshTokenStore,
    private val clock: Clock
) {

    @Transactional
    fun execute(command: SignOutCommand) {
        val verifiedRefresh = tokenVerifier.verifyRefresh(command.rawRefreshToken)
        val verifiedAccess = tokenVerifier.verifyAccess(command.rawAccessToken)

        if (verifiedAccess.subject.userId != verifiedRefresh.subject.userId) {
            throw TokenInvalidException()
        }

        refreshTokenStore.delete(verifiedRefresh.subject.userId)

        val now = clock.instant()

        val accessTtl = Duration.between(now, verifiedAccess.expiresAt)
        if (!accessTtl.isNegative && !accessTtl.isZero) {
            refreshTokenStore.blacklistJti(verifiedAccess.jti, accessTtl)
        }

        val refreshTtl = Duration.between(now, verifiedRefresh.expiresAt)
        if (!refreshTtl.isNegative && !refreshTtl.isZero) {
            refreshTokenStore.blacklistJti(verifiedRefresh.jti, refreshTtl)
        }
    }
}