package io.chronos.saga.testkit

import io.chronos.saga.OrderCtx
import io.chronos.saga.PlaceOrderScenario
import io.chronos.saga.dsl.saga
import io.chronos.saga.engine.SagaOutcome
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class ModelCheckerTest {
    /** 명세의 두 불변식. */
    private val moneyInvariant = invariant<PlaceOrderScenario>("돈이 증발하지 않는다") { r ->
        val p = r.scenario.payment
        when (r.outcome) {
            SagaOutcome.COMPLETED -> p.reserved - p.released == r.scenario.context.amount
            else -> p.reserved - p.released == 0L
        }
    }
    private val terminalInvariant = invariant<PlaceOrderScenario>("항상 터미널 상태 도달") { it.isTerminal }

    @Test
    fun `데모 - 보상 재시도가 없는 정의에서 '보상 타임아웃 - 재시도 소진' 반례를 잡아낸다`() {
        // 의도적 결함: compensationRetries = 1 → 보상 첫 시도가 타임아웃이면 그대로 소진
        val checker = ModelChecker({ PlaceOrderScenario(compensationRetries = 1) })

        val result = checker.check(moneyInvariant, terminalInvariant)

        result.violations.shouldNotBeEmpty()
        result.pathsExplored shouldBe 64 // 4^3

        // 반례에는 반드시 "보상 타임아웃 주입 + 뒤 step 실패" 조합이 포함된다
        val counterexample = result.violations.first { violation ->
            violation.result.injected.containsValue(InjectedOutcome.COMPENSATION_TIMEOUT)
        }
        counterexample.result.outcome shouldBe SagaOutcome.STUCK

        val pretty = CheckResult.prettyCounterexample(counterexample)
        println(pretty) // 데모: 사람이 읽는 반례 경로
        pretty shouldContain "주입된 결함"
        pretty shouldContain "COMPENSATION_TIMEOUT"
        pretty shouldContain "CompensationFailed"
        pretty shouldContain "터미널 아님"

        // 돈 불변식도 함께 무너진다: 결제는 잡혔는데 해제가 영영 안 됨
        result.violations.any { it.invariantName == "돈이 증발하지 않는다" }.shouldBeTrue()
    }

    @Test
    fun `수정본 - 보상 재시도를 설정하면 전 경로에서 불변식이 성립한다`() {
        val checker = ModelChecker({ PlaceOrderScenario(compensationRetries = 3) })

        val result = checker.check(moneyInvariant, terminalInvariant)

        result.pathsExplored shouldBe 64
        result.assertNoViolations()
    }

    @Test
    fun `step이 6개를 넘으면 상태공간 상한 초과로 명시적 에러`() {
        val tooBig = saga<OrderCtx>("TooBig") {
            (1..7).forEach { n -> step("step$n") { action {} } }
        }
        val scenario = object : SagaScenario<OrderCtx> {
            override val definition = tooBig
            override val context = OrderCtx("x", 1)
        }

        shouldThrow<StateSpaceExceededException> {
            ModelChecker({ scenario }).check()
        }.message shouldContain "상태공간 상한 초과"
    }
}
