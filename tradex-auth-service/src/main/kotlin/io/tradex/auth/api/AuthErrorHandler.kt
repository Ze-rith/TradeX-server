package io.tradex.auth.api

import io.tradex.auth.domain.AccountLockedException
import io.tradex.auth.domain.EmailAlreadyExistsException
import io.tradex.auth.domain.InvalidCredentialException
import io.tradex.auth.domain.InvalidEmailException
import io.tradex.auth.domain.TokenInvalidException
import io.tradex.auth.domain.WeakPasswordException
import io.tradex.common.BaseResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** code 값은 registration-service HTTP 어댑터가 그대로 전달하므로 구체적으로 유지한다. */
@RestControllerAdvice
class AuthErrorHandler {
    @ExceptionHandler(InvalidCredentialException::class, TokenInvalidException::class)
    fun unauthorized(e: RuntimeException) = respond(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e)

    @ExceptionHandler(AccountLockedException::class)
    fun locked(e: AccountLockedException) = respond(HttpStatus.LOCKED, "ACCOUNT_LOCKED", e)

    @ExceptionHandler(EmailAlreadyExistsException::class)
    fun emailDuplicate(e: EmailAlreadyExistsException) = respond(HttpStatus.CONFLICT, "EMAIL_DUPLICATE", e)

    @ExceptionHandler(InvalidEmailException::class)
    fun invalidEmail(e: InvalidEmailException) = respond(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", e)

    @ExceptionHandler(WeakPasswordException::class)
    fun weakPassword(e: WeakPasswordException) = respond(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", e)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidBody(e: MethodArgumentNotValidException): ResponseEntity<BaseResponse<Unit>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(BaseResponse.error("INVALID_REQUEST", e.bindingResult.fieldErrors.joinToString { "${it.field}: ${it.defaultMessage}" }))

    private fun respond(status: HttpStatus, code: String, e: RuntimeException): ResponseEntity<BaseResponse<Unit>> =
        ResponseEntity.status(status).body(BaseResponse.error(code, e.message ?: code))
}
