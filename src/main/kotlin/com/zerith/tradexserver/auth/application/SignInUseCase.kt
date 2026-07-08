package com.zerith.tradexserver.auth.application

import com.zerith.tradexserver.auth.application.command.SignInCommand
import com.zerith.tradexserver.auth.domain.event.SignInFailedEvent
import com.zerith.tradexserver.auth.domain.event.UserSignedInEvent
import com.zerith.tradexserver.auth.domain.exception.InvalidCredentialException
import com.zerith.tradexserver.auth.domain.model.Email
import com.zerith.tradexserver.auth.domain.policy.LockPolicy
import com.zerith.tradexserver.auth.domain.port.PasswordHasher
import com.zerith.tradexserver.auth.domain.port.TokenIssuer
import com.zerith.tradexserver.auth.domain.repository.RefreshTokenStore
import com.zerith.tradexserver.auth.domain.repository.UserRepository
import com.zerith.tradexserver.auth.domain.token.TokenPair
import com.zerith.tradexserver.auth.domain.token.TokenSubject
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Component
class SignInUseCase(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher,
    private val tokenIssuer: TokenIssuer,
    private val refreshTokenStore: RefreshTokenStore,
    private val lockPolicy: LockPolicy,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock
) {

    @Transactional
    fun execute(command: SignInCommand): TokenPair {
        val email = Email.of(command.email)
        val now: Instant = clock.instant()

        val user = userRepository.findByEmail(email)
            ?: throw InvalidCredentialException()

        user.assertNotLocked(now)

        val matched = passwordHasher.matches(command.rawPassword, user.credential.passwordHash)
        if (!matched) {
            user.recordSignInFailure(now, lockPolicy)
            userRepository.save(user)
            eventPublisher.publishEvent(
                SignInFailedEvent(user.id, now)
            )
            throw InvalidCredentialException()
        }

        user.recordSignInSuccess(now)
        userRepository.save(user)

        val subject = TokenSubject.of(user.id, DEFAULT_ROLE)
        val tokenPair = tokenIssuer.issue(subject)

        val refreshTtl = Duration.between(now, tokenPair.refreshToken.expiresAt)
        refreshTokenStore.save(
            userId = user.id,
            jti = tokenPair.refreshToken.jti,
            ttl = refreshTtl
        )

        eventPublisher.publishEvent(
            UserSignedInEvent(user.id, now)
        )

        return tokenPair
    }

    companion object {
        private const val DEFAULT_ROLE = "USER"
    }
}