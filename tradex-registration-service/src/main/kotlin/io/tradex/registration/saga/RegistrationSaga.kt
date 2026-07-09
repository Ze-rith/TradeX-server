package io.tradex.registration.saga

import io.tradex.saga.SagaDefinition
import io.tradex.saga.dsl.saga
import io.tradex.registration.port.MemberProvisioningPort
import io.tradex.registration.port.PreparedMember
import io.tradex.registration.port.UserProvisioningPort
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class RegisterAccountCtx(
    val userId: String,
    val email: String,
    val passwordHash: String,
    val encryptedName: String,
    val encryptedBirthDate: String,
    val encryptedPhoneNumber: String,
    val phoneNumberHash: String,
)

fun registerAccountSaga(
    users: UserProvisioningPort,
    members: MemberProvisioningPort,
): SagaDefinition<RegisterAccountCtx> = saga("RegisterAccount") {
    step("registerUser") {
        action { it.ctx.run { users.registerUser(userId, email, passwordHash) } }
        compensate { users.revokeUser(it.ctx.userId, reason = "registration saga compensated") }
        timeout(3.seconds)
        retry(times = 2, backoff = io.tradex.saga.exponential(100.milliseconds))
        compensationRetry(times = 3)
    }
    step("createMember") {
        action {
            it.ctx.run {
                members.createMember(
                    memberId = userId,
                    member = PreparedMember(encryptedName, encryptedBirthDate, encryptedPhoneNumber, phoneNumberHash),
                )
            }
        }
        compensate { members.revokeMember(it.ctx.userId, reason = "registration saga compensated") }
        timeout(3.seconds)
        retry(times = 2, backoff = io.tradex.saga.exponential(100.milliseconds))
        compensationRetry(times = 3)
    }
}
