package io.tradex.member.api

import com.fasterxml.jackson.annotation.JsonFormat
import io.tradex.core.event.AggregateId
import io.tradex.common.BaseResponse
import io.tradex.member.application.MemberProvisioningService
import io.tradex.member.application.PreparedMember
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate
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

data class PrepareMemberRequest(
    @field:NotBlank @field:Size(min = 1, max = 50) val name: String,
    @field:NotNull @field:JsonFormat(pattern = "yyyy-MM-dd") val birthDate: LocalDate,
    @field:NotBlank @field:Size(max = 20) val phoneNumber: String,
)

data class CreateMemberRequest(
    @field:NotBlank val encryptedName: String,
    @field:NotBlank val encryptedBirthDate: String,
    @field:NotBlank val encryptedPhoneNumber: String,
    @field:NotBlank val phoneNumberHash: String,
)

/** registration-service 전용 내부 API. PUT/DELETE는 사가 재시도에 대해 멱등. */
@RestController
@RequestMapping("/internal")
class InternalMemberController(private val provisioning: MemberProvisioningService) {
    @PostMapping("/members/prepare")
    fun prepare(@Valid @RequestBody request: PrepareMemberRequest): BaseResponse<PreparedMember> =
        BaseResponse.ok(provisioning.prepareMember(request.name, request.birthDate, request.phoneNumber))

    @PutMapping("/members/{memberId}")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable memberId: String, @Valid @RequestBody request: CreateMemberRequest): BaseResponse<Unit> {
        provisioning.create(
            AggregateId.of(memberId),
            PreparedMember(request.encryptedName, request.encryptedBirthDate, request.encryptedPhoneNumber, request.phoneNumberHash),
        )
        return BaseResponse.ok()
    }

    @DeleteMapping("/members/{memberId}")
    fun revoke(@PathVariable memberId: String, @RequestParam(defaultValue = "compensated") reason: String): BaseResponse<Unit> {
        provisioning.revoke(AggregateId.of(memberId), reason)
        return BaseResponse.ok()
    }
}
