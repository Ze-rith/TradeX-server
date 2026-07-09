package io.chronos.tradex.api

import io.chronos.tradex.auth.domain.AccountLockedException
import io.chronos.tradex.auth.domain.AuthException
import io.chronos.tradex.auth.domain.EmailAlreadyExistsException
import io.chronos.tradex.auth.domain.InvalidCredentialException
import io.chronos.tradex.auth.domain.TokenInvalidException
import io.chronos.tradex.common.BaseResponse
import io.chronos.tradex.member.domain.MemberException
import io.chronos.tradex.member.domain.PhoneNumberAlreadyExistsException
import io.chronos.tradex.registration.RegistrationFailedException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class TradexErrorHandler {
    @ExceptionHandler(InvalidCredentialException::class, TokenInvalidException::class)
    fun unauthorized(e: AuthException): ResponseEntity<BaseResponse<Unit>> =
        respond(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e)

    @ExceptionHandler(AccountLockedException::class)
    fun locked(e: AccountLockedException): ResponseEntity<BaseResponse<Unit>> =
        respond(HttpStatus.LOCKED, "ACCOUNT_LOCKED", e)

    @ExceptionHandler(EmailAlreadyExistsException::class, PhoneNumberAlreadyExistsException::class)
    fun conflict(e: RuntimeException): ResponseEntity<BaseResponse<Unit>> =
        respond(HttpStatus.CONFLICT, "DUPLICATE", e)

    @ExceptionHandler(AuthException::class, MemberException::class)
    fun badRequest(e: RuntimeException): ResponseEntity<BaseResponse<Unit>> =
        respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidBody(e: MethodArgumentNotValidException): ResponseEntity<BaseResponse<Unit>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(BaseResponse.error("INVALID_REQUEST", e.bindingResult.fieldErrors.joinToString { "${it.field}: ${it.defaultMessage}" }))

    @ExceptionHandler(RegistrationFailedException::class)
    fun registrationFailed(e: RegistrationFailedException): ResponseEntity<BaseResponse<Unit>> =
        respond(HttpStatus.CONFLICT, "REGISTRATION_FAILED", e)

    private fun respond(status: HttpStatus, code: String, e: RuntimeException): ResponseEntity<BaseResponse<Unit>> =
        ResponseEntity.status(status).body(BaseResponse.error(code, e.message ?: code))
}
