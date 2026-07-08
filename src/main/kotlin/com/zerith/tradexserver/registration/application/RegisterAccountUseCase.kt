package com.zerith.tradexserver.registration.application

import com.zerith.tradexserver.auth.application.RegisterUserUseCase
import com.zerith.tradexserver.auth.application.command.RegisterUserCommand
import com.zerith.tradexserver.auth.domain.model.UserId
import com.zerith.tradexserver.member.application.CreateMemberUseCase
import com.zerith.tradexserver.member.application.command.CreateMemberCommand
import com.zerith.tradexserver.registration.application.command.RegisterAccountCommand
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class RegisterAccountUseCase(
    private val registerUserUseCase: RegisterUserUseCase,
    private val createMemberUseCase: CreateMemberUseCase
) {

    @Transactional
    fun execute(command: RegisterAccountCommand): UserId {
        val userId = registerUserUseCase.execute(
            RegisterUserCommand.of(command.email, command.rawPassword)
        )

        createMemberUseCase.execute(
            CreateMemberCommand.of(
                memberId = userId.value,
                name = command.name,
                birthDate = command.birthDate,
                phoneNumber = command.phoneNumber
            )
        )

        return userId
    }
}