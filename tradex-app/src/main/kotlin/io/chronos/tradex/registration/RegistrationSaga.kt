package io.chronos.tradex.registration

import io.chronos.cell.CellFabric
import io.chronos.core.event.AggregateId
import io.chronos.core.event.DomainEvent
import io.chronos.saga.SagaDefinition
import io.chronos.saga.dsl.saga
import io.chronos.tradex.auth.contract.UserRegistered
import io.chronos.tradex.auth.contract.UserRegistrationRevoked
import io.chronos.tradex.member.contract.MemberCreated
import io.chronos.tradex.member.contract.MemberCreationRevoked
import io.chronos.tradex.member.domain.MemberIds
import java.time.Clock
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

/**
 * 사가 컨텍스트는 순수 데이터 — SagaStarted에 직렬화되어 크래시 후에도 복원된다.
 * PII는 여기서도 암호문이다.
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
 * 레거시의 단일 @Transactional 등록을 크로스 애그리게잇 사가로 대체 (DECISIONS.md D21).
 * User와 Member는 서로 다른 셀에 있을 수 있으므로 원자적 트랜잭션이 불가능하다 —
 * 대신 보상(각각 Revoked 이벤트)으로 "함께 존재하거나 함께 사라진다"를 보장하고,
 * 그 보장은 모델 체커가 전 경로 검증한다.
 *
 * 액션 멱등성: 재시도는 스트림에 이미 목표 이벤트가 있으면 no-op (append의 낙관적
 * 동시성이 최후 방어선).
 */
fun registerAccountSaga(fabric: CellFabric, clock: Clock): SagaDefinition<RegisterAccountCtx> =
    saga("RegisterAccount") {
        step("registerUser") {
            action { ctx ->
                appendOnce(fabric, AggregateId.of(ctx.ctx.userId), "User", UserRegistered::class) { id ->
                    UserRegistered(id, clock.instant(), ctx.ctx.email, ctx.ctx.passwordHash)
                }
            }
            compensate { ctx ->
                appendOnce(fabric, AggregateId.of(ctx.ctx.userId), "User", UserRegistrationRevoked::class) { id ->
                    UserRegistrationRevoked(id, clock.instant(), reason = "registration saga compensated")
                }
            }
            timeout(2.seconds)
            retry(times = 2)
            compensationRetry(times = 3)
        }
        step("createMember") {
            action { ctx ->
                appendOnce(fabric, MemberIds.of(AggregateId.of(ctx.ctx.userId)), "Member", MemberCreated::class) { id ->
                    MemberCreated(
                        aggregateId = id,
                        validTime = clock.instant(),
                        encryptedName = ctx.ctx.encryptedName,
                        encryptedBirthDate = ctx.ctx.encryptedBirthDate,
                        encryptedPhoneNumber = ctx.ctx.encryptedPhoneNumber,
                        phoneNumberHash = ctx.ctx.phoneNumberHash,
                    )
                }
            }
            compensate { ctx ->
                appendOnce(fabric, MemberIds.of(AggregateId.of(ctx.ctx.userId)), "Member", MemberCreationRevoked::class) { id ->
                    MemberCreationRevoked(id, clock.instant(), reason = "registration saga compensated")
                }
            }
            timeout(2.seconds)
            retry(times = 2)
            compensationRetry(times = 3)
        }
    }

private fun <E : DomainEvent> appendOnce(
    fabric: CellFabric,
    aggregateId: AggregateId,
    aggregateType: String,
    eventClass: KClass<E>,
    build: (AggregateId) -> E,
) {
    val stream = fabric.readStream(aggregateId)
    if (stream.any { eventClass.isInstance(it.event) }) return // 재시도 멱등
    fabric.append(aggregateType, aggregateId, stream.lastOrNull()?.seqNo ?: 0L, listOf(build(aggregateId)))
}
