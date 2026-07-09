package io.chronos.tradex.registration.api

import com.fasterxml.jackson.annotation.JsonFormat
import io.chronos.tradex.common.BaseResponse
import io.chronos.tradex.registration.application.RegistrationFailedException
import io.chronos.tradex.registration.application.RegistrationService
import io.chronos.tradex.registration.port.ProvisioningRejectedException
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice

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
            email = request.email,
            rawPassword = request.password,
            name = request.name,
            birthDate = request.birthDate,
            phoneNumber = request.phoneNumber,
        )
        return BaseResponse.ok(RegisterAccountResponse(userId.toString()))
    }
}

@RestControllerAdvice
class RegistrationErrorHandler {
    /** 다운스트림 거절(중복/정책 위반)은 status·code·message를 그대로 클라이언트에 전달한다. */
    @ExceptionHandler(ProvisioningRejectedException::class)
    fun rejected(e: ProvisioningRejectedException): ResponseEntity<BaseResponse<Unit>> =
        ResponseEntity.status(e.status).body(BaseResponse.error(e.code, e.message))

    @ExceptionHandler(RegistrationFailedException::class)
    fun sagaFailed(e: RegistrationFailedException): ResponseEntity<BaseResponse<Unit>> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(BaseResponse.error("REGISTRATION_FAILED", e.message ?: "failed"))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidBody(e: MethodArgumentNotValidException): ResponseEntity<BaseResponse<Unit>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(BaseResponse.error("INVALID_REQUEST", e.bindingResult.fieldErrors.joinToString { "${it.field}: ${it.defaultMessage}" }))
}
