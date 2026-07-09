package io.tradex.registration.saga

import io.tradex.saga.SagaDefinition
import io.tradex.saga.dsl.saga
import io.tradex.registration.port.MemberProvisioningPort
import io.tradex.registration.port.PreparedMember
import io.tradex.registration.port.UserProvisioningPort
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 사가 컨텍스트는 순수 데이터 + 비밀은 해시/암호문만 (SagaStarted에 직렬화되어 저장되므로).
 * 평문 비밀번호·PII는 프리페어 단계에서 각 소유 서비스가 해시/암호화한 뒤 버려진다.
 */
data class RegisterAccountCtx(
    val userId: String,
    val email: String,
    val passwordHash: String,
    val encryptedName: String,
    val encryptedBirthDate: String,
    val encryptedPhoneNumber: String,
    val phoneNumberHash: String,
)

/**
 * 서비스 경계를 넘는 등록 사가: auth-service에 User 생성 → member-service에 Member 생성.
 * 어느 쪽이 실패하든 역순 보상(DELETE)으로 "계정과 멤버는 함께 존재하거나 함께 사라진다".
 * step 액션은 HTTP 포트 뒤에 있으므로 모델 체커는 fake 포트로 전 경로를 탐색한다.
 */
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
                    memberId = userId, // API 표면의 memberId = userId (레거시 계약)
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
