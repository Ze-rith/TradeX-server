package io.tradex.auth.api

import io.tradex.core.event.AggregateId
import io.tradex.auth.application.PreparedCredential
import io.tradex.auth.application.UserProvisioningService
import io.tradex.common.BaseResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class PrepareCredentialRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank val password: String,
)

data class RegisterUserRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank val passwordHash: String,
)

/**
 * registration-service 전용 내부 API (서비스 간 신뢰 경계 내부).
 * PUT/DELETE는 사가 step/보상에서 재시도되므로 멱등이다.
 */
@RestController
@RequestMapping("/internal")
class InternalUserController(private val provisioning: UserProvisioningService) {
    @PostMapping("/credentials")
    fun prepareCredential(@Valid @RequestBody request: PrepareCredentialRequest): BaseResponse<PreparedCredential> =
        BaseResponse.ok(provisioning.prepareCredential(request.email, request.password))

    @PutMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@PathVariable userId: String, @Valid @RequestBody request: RegisterUserRequest): BaseResponse<Unit> {
        provisioning.register(AggregateId.of(userId), request.email, request.passwordHash)
        return BaseResponse.ok()
    }

    @DeleteMapping("/users/{userId}")
    fun revoke(@PathVariable userId: String, @RequestParam(defaultValue = "compensated") reason: String): BaseResponse<Unit> {
        provisioning.revoke(AggregateId.of(userId), reason)
        return BaseResponse.ok()
    }
}
