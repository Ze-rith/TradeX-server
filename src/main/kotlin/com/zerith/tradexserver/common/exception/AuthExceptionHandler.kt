package com.zerith.tradexserver.common.exception

import com.zerith.tradexserver.auth.domain.exception.AccountLockedException
import com.zerith.tradexserver.auth.domain.exception.AuthException
import com.zerith.tradexserver.auth.domain.exception.EmailAlreadyExistsException
import com.zerith.tradexserver.auth.domain.exception.InvalidCredentialException
import com.zerith.tradexserver.auth.domain.exception.InvalidEmailException
import com.zerith.tradexserver.auth.domain.exception.TokenInvalidException
import com.zerith.tradexserver.auth.domain.exception.WeakPasswordException
import com.zerith.tradexserver.common.response.BaseResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import com.zerith.tradexserver.member.domain.exception.MemberException
import com.zerith.tradexserver.member.domain.exception.PhoneNumberAlreadyExistsException


@RestControllerAdvice
class AuthExceptionHandler {

    private val log = LoggerFactory.getLogger(AuthExceptionHandler::class.java)

    @ExceptionHandler(InvalidCredentialException::class)
    fun handleInvalidCredential(e: InvalidCredentialException): ResponseEntity<BaseResponse<Unit>> {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(BaseResponse.error("AUTH_INVALID_CREDENTIAL", "invalid credential"))
    }

    @ExceptionHandler(AccountLockedException::class)
    fun handleAccountLocked(e: AccountLockedException): ResponseEntity<BaseResponse<Unit>> {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(BaseResponse.error("AUTH_INVALID_CREDENTIAL", "invalid credential"))
    }

    @ExceptionHandler(TokenInvalidException::class)
    fun handleTokenInvalid(e: TokenInvalidException): ResponseEntity<BaseResponse<Unit>> {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(BaseResponse.error("AUTH_INVALID_TOKEN", "invalid token"))
    }

    @ExceptionHandler(InvalidEmailException::class)
    fun handleInvalidEmail(e: InvalidEmailException): ResponseEntity<BaseResponse<Unit>> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(BaseResponse.error("AUTH_INVALID_REQUEST", "invalid request"))
    }

    @ExceptionHandler(WeakPasswordException::class)
    fun handleWeakPassword(e: WeakPasswordException): ResponseEntity<BaseResponse<Unit>> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(BaseResponse.error("AUTH_INVALID_REQUEST", "invalid request"))
    }

    @ExceptionHandler(EmailAlreadyExistsException::class)
    fun handleEmailExists(e: EmailAlreadyExistsException): ResponseEntity<BaseResponse<Unit>> {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(BaseResponse.error("AUTH_INVALID_REQUEST", "invalid request"))
    }

    @ExceptionHandler(AuthException::class)
    fun handleAuth(e: AuthException): ResponseEntity<BaseResponse<Unit>> {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(BaseResponse.error("AUTH_FAILED", "invalid request"))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<BaseResponse<Unit>> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(BaseResponse.error("VALIDATION_FAILED", "invalid request"))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<BaseResponse<Unit>> {
        log.error("unexpected error", e)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(BaseResponse.error("INTERNAL_ERROR", "internal error"))
    }

    @ExceptionHandler(MemberException::class)
    fun handleMember(e: MemberException): ResponseEntity<BaseResponse<Unit>> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(BaseResponse.error("INVALID_REQUEST", "invalid request"))
    }

    @ExceptionHandler(PhoneNumberAlreadyExistsException::class)
    fun handlePhoneExists(e: PhoneNumberAlreadyExistsException): ResponseEntity<BaseResponse<Unit>> {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(BaseResponse.error("INVALID_REQUEST", "invalid request"))
    }
}