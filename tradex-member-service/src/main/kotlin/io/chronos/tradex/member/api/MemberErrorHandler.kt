package io.chronos.tradex.member.api

import io.chronos.tradex.common.BaseResponse
import io.chronos.tradex.member.domain.InvalidBirthDateException
import io.chronos.tradex.member.domain.InvalidNameException
import io.chronos.tradex.member.domain.InvalidPhoneNumberException
import io.chronos.tradex.member.domain.PhoneNumberAlreadyExistsException
import io.chronos.tradex.member.domain.UnderageException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** code 값은 registration-service HTTP 어댑터가 그대로 전달하므로 구체적으로 유지한다. */
@RestControllerAdvice
class MemberErrorHandler {
    @ExceptionHandler(PhoneNumberAlreadyExistsException::class)
    fun phoneDuplicate(e: PhoneNumberAlreadyExistsException) = respond(HttpStatus.CONFLICT, "PHONE_DUPLICATE", e)

    @ExceptionHandler(InvalidNameException::class)
    fun invalidName(e: InvalidNameException) = respond(HttpStatus.BAD_REQUEST, "INVALID_NAME", e)

    @ExceptionHandler(InvalidBirthDateException::class)
    fun invalidBirthDate(e: InvalidBirthDateException) = respond(HttpStatus.BAD_REQUEST, "INVALID_BIRTHDATE", e)

    @ExceptionHandler(UnderageException::class)
    fun underage(e: UnderageException) = respond(HttpStatus.BAD_REQUEST, "UNDERAGE", e)

    @ExceptionHandler(InvalidPhoneNumberException::class)
    fun invalidPhone(e: InvalidPhoneNumberException) = respond(HttpStatus.BAD_REQUEST, "INVALID_PHONE", e)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidBody(e: MethodArgumentNotValidException): ResponseEntity<BaseResponse<Unit>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(BaseResponse.error("INVALID_REQUEST", e.bindingResult.fieldErrors.joinToString { "${it.field}: ${it.defaultMessage}" }))

    private fun respond(status: HttpStatus, code: String, e: RuntimeException): ResponseEntity<BaseResponse<Unit>> =
        ResponseEntity.status(status).body(BaseResponse.error(code, e.message ?: code))
}
