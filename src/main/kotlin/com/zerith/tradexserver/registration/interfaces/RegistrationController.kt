package com.zerith.tradexserver.registration.interfaces

import com.zerith.tradexserver.common.response.BaseResponse
import com.zerith.tradexserver.registration.application.RegisterAccountUseCase
import com.zerith.tradexserver.registration.application.command.RegisterAccountCommand
import com.zerith.tradexserver.registration.data.RegisterAccountRequest
import com.zerith.tradexserver.registration.data.RegisterAccountResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/registration")
class RegistrationController(
    private val registerAccountUseCase: RegisterAccountUseCase
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun register(
        @Valid
        @RequestBody
        request: RegisterAccountRequest
    ): BaseResponse<RegisterAccountResponse> {
        val userId = registerAccountUseCase.execute(
            RegisterAccountCommand.of(
                email = request.email,
                rawPassword = request.password,
                name = request.name,
                birthDate = request.birthDate,
                phoneNumber = request.phoneNumber
            )
        )
        return BaseResponse.ok(
            RegisterAccountResponse.of(userId.asString())
        )
    }
}