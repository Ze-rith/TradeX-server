package io.chronos.tradex.registration

import io.chronos.cell.CellFabric
import io.chronos.core.event.AggregateId
import io.chronos.core.query.AggregateRepository
import io.chronos.saga.testkit.ModelChecker
import io.chronos.saga.testkit.SagaScenario
import io.chronos.saga.testkit.invariant
import io.chronos.tradex.auth.domain.UserAggregate
import io.chronos.tradex.member.domain.MemberAggregate
import io.chronos.tradex.member.domain.MemberIds
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Test

/**
 * 레거시의 단일 @Transactional이 보장하던 "계정과 멤버의 원자성"을,
 * 사가 + 보상으로 대체한 뒤 모델 체커로 전 경로(4^2=16) 증명한다 (DECISIONS.md D21).
 */
class RegistrationSagaModelCheckTest {
    class RegisterScenario : SagaScenario<RegisterAccountCtx> {
        val fabric = CellFabric(cellCount = 1)
        val userId: AggregateId = AggregateId.new()

        override val context = RegisterAccountCtx(
            userId = userId.toString(),
            email = "zerith@example.com",
            passwordHash = "bcrypt-hash",
            encryptedName = "enc-name",
            encryptedBirthDate = "enc-birth",
            encryptedPhoneNumber = "enc-phone",
            phoneNumberHash = "phone-hash",
        )
        override val definition = registerAccountSaga(
            fabric,
            Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC),
        )

        fun userExists(): Boolean =
            AggregateRepository(UserAggregate, fabric.cells.getValue(0).store).currentState(userId).exists

        fun memberExists(): Boolean {
            val memberId = MemberIds.of(userId)
            return AggregateRepository(MemberAggregate, fabric.cells.getValue(0).store).currentState(memberId).exists
        }
    }

    @Test
    fun `모든 결함 조합에서 - 계정과 멤버는 함께 존재하거나 함께 사라진다`() {
        val checker = ModelChecker({ RegisterScenario() })

        val result = checker.check(
            invariant("항상 터미널 상태 도달") { it.isTerminal },
            invariant("계정과 멤버는 함께 존재하거나 함께 사라진다 - 유령 계정 금지") { r ->
                r.scenario.userExists() == r.scenario.memberExists()
            },
        )

        result.pathsExplored shouldBe 16 // 4^2
        result.assertNoViolations()
    }
}
