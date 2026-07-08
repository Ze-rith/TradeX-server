package com.zerith.tradexserver.auth.application

import com.zerith.tradexserver.auth.application.command.RegisterUserCommand
import com.zerith.tradexserver.auth.domain.event.UserRegisteredEvent
import com.zerith.tradexserver.auth.domain.exception.EmailAlreadyExistsException
import com.zerith.tradexserver.auth.domain.model.Credential
import com.zerith.tradexserver.auth.domain.model.Email
import com.zerith.tradexserver.auth.domain.model.User
import com.zerith.tradexserver.auth.domain.model.UserId
import com.zerith.tradexserver.auth.domain.policy.PasswordPolicy
import com.zerith.tradexserver.auth.domain.port.PasswordHasher
import com.zerith.tradexserver.auth.domain.repository.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Component
class RegisterUserUseCase(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher,
    private val passwordPolicy: PasswordPolicy,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock
) {

    @Transactional
    fun execute(command: RegisterUserCommand): UserId {
        val email = Email.of(command.email)

        if (userRepository.existsByEmail(email)) {
            throw EmailAlreadyExistsException()
        }

        passwordPolicy.validate(command.rawPassword, email)

        val now: Instant = clock.instant()
        val passwordHash = passwordHasher.hash(command.rawPassword)
        val credential = Credential.issue(passwordHash, now)
        val user = User.register(email, credential, now)

        val saved = userRepository.save(user)

        eventPublisher.publishEvent(
            UserRegisteredEvent(saved.id, now)
        )

        return saved.id
    }
}