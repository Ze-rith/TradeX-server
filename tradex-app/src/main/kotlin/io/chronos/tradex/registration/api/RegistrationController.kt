package io.chronos.tradex.registration.api

import com.fasterxml.jackson.annotation.JsonFormat
import io.chronos.tradex.common.BaseResponse
import io.chronos.tradex.registration.RegistrationService
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 레거시 RegisterAccountRequest와 동일 계약. */
data class RegisterAccountRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank @field:Size(min = 12, max = 128) val password: String,
    @field:NotBlank @field:Size(min = 1, max = 50) val name: String,
    @field:NotNull @field:JsonFormat(pattern = "yyyy-MM-dd") val birthDate: LocalDate,
    @field:NotBlank @field:Size(max = 20) val phoneNumber: String,
)

data class RegisterAccountResponse(val userId: String)

@RestController
@RequestMapping("/api/v1/registration")
class RegistrationController(private val registrationService: RegistrationService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterAccountRequest): BaseResponse<RegisterAccountResponse> {
        val userId = registrationService.register(
            rawEmail = request.email,
            rawPassword = request.password,
            rawName = request.name,
            rawBirthDate = request.birthDate,
            rawPhoneNumber = request.phoneNumber,
        )
        return BaseResponse.ok(RegisterAccountResponse(userId.toString()))
    }
}
