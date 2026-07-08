package com.zerith.tradexserver.auth.application

import com.zerith.tradexserver.auth.application.command.ValidateAccessTokenCommand
import com.zerith.tradexserver.auth.domain.exception.TokenInvalidException
import com.zerith.tradexserver.auth.domain.port.TokenVerifier
import com.zerith.tradexserver.auth.domain.repository.RefreshTokenStore
import com.zerith.tradexserver.auth.domain.token.TokenSubject
import org.springframework.stereotype.Component

@Component
class ValidateAccessTokenUseCase(
    private val tokenVerifier: TokenVerifier,
    private val refreshTokenStore: RefreshTokenStore
) {

    fun execute(command: ValidateAccessTokenCommand): TokenSubject {
        val verified = tokenVerifier.verifyAccess(command.rawAccessToken)

        if (refreshTokenStore.isBlacklisted(verified.jti)) {
            throw TokenInvalidException()
        }

        return verified.subject
    }
}