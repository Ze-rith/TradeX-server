package io.tradex.registration

import io.tradex.saga.testkit.ModelChecker
import io.tradex.saga.testkit.SagaScenario
import io.tradex.saga.testkit.invariant
import io.tradex.registration.saga.RegisterAccountCtx
import io.tradex.registration.saga.registerAccountSaga
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * 서비스 경계를 넘는 등록 사가의 전 경로(4^2=16) 검증.
 * step 액션이 HTTP 포트 뒤에 있으므로 fake 포트로 결함을 주입한다 —
 * 원격 서비스가 죽거나 느려도 "계정과 멤버는 함께 존재하거나 함께 사라진다".
 */
class RegistrationSagaModelCheckTest {
    class Scenario : SagaScenario<RegisterAccountCtx> {
        val users = FakeUserPort()
        val members = FakeMemberPort()
        private val userId = "01907e2e-0000-7000-8000-00000000000a"

        override val context = RegisterAccountCtx(
            userId = userId,
            email = "zerith@example.com",
            passwordHash = "bcrypt-hash",
            encryptedName = "enc-name",
            encryptedBirthDate = "enc-birth",
            encryptedPhoneNumber = "enc-phone",
            phoneNumberHash = "phone-hash",
        )
        override val definition = registerAccountSaga(users, members)

        fun userExists() = users.userExists(context.userId)
        fun memberExists() = members.memberExists(context.userId)
    }

    @Test
    fun `모든 결함 조합에서 - 계정과 멤버는 함께 존재하거나 함께 사라진다`() {
        val result = ModelChecker({ Scenario() }).check(
            invariant("항상 터미널 상태 도달") { it.isTerminal },
            invariant("계정과 멤버는 함께 존재하거나 함께 사라진다 - 유령 계정 금지") { r ->
                r.scenario.userExists() == r.scenario.memberExists()
            },
        )

        result.pathsExplored shouldBe 16
        result.assertNoViolations()
    }
}
