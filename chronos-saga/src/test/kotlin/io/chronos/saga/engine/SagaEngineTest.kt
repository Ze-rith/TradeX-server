package io.chronos.saga.engine

import io.chronos.core.event.AggregateId
import io.chronos.core.store.InMemoryEventStore
import io.chronos.saga.OrderCtx
import io.chronos.saga.PlaceOrderScenario
import io.chronos.saga.SagaActionContext
import io.chronos.saga.SagaStep
import io.chronos.saga.StepExecutor
import io.chronos.saga.StepResult
import io.chronos.saga.testkit.InjectedOutcome
import io.chronos.saga.testkit.SimulatedStepExecutor
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class SagaEngineTest {
    private val store = InMemoryEventStore()
    private val sagaId = AggregateId.new()
    private val codec = JacksonSagaContextCodec(OrderCtx::class.java)

    private fun engine(scenario: PlaceOrderScenario, executor: StepExecutor<OrderCtx>) =
        SagaEngine(scenario.definition, store, executor, codec, sleeper = {})

    @Test
    fun `해피 패스 - 전 step 성공하면 SagaCompleted로 끝난다`() {
        val scenario = PlaceOrderScenario(compensationRetries = 1)
        val outcome = engine(scenario, SimulatedStepExecutor(emptyMap())).start(sagaId, scenario.context)

        outcome shouldBe SagaOutcome.COMPLETED
        eventNames() shouldContainInOrder listOf(
            "SagaStarted",
            "StepSucceeded", "StepSucceeded", "StepSucceeded",
            "SagaCompleted",
        )
        scenario.payment.reserved shouldBe 10_000
        scenario.shipments shouldBe listOf("order-1")
    }

    @Test
    fun `중간 step 영구 실패 - 앞선 step들이 역순으로 보상되고 SagaCompensated`() {
        val scenario = PlaceOrderScenario(compensationRetries = 1)
        val executor = SimulatedStepExecutor<OrderCtx>(mapOf("deductStock" to InjectedOutcome.FAIL))

        val outcome = engine(scenario, executor).start(sagaId, scenario.context)

        outcome shouldBe SagaOutcome.COMPENSATED
        eventNames() shouldContainInOrder listOf(
            "SagaStarted",
            "StepSucceeded",                 // reservePayment
            "StepFailed", "StepFailed",      // deductStock 시도 2회 소진
            "CompensationStarted",
            "CompensationSucceeded",         // reservePayment 해제
            "SagaCompensated",
        )
        scenario.payment.reserved shouldBe 10_000
        scenario.payment.released shouldBe 10_000
    }

    @Test
    fun `일시 타임아웃은 재시도로 회복된다`() {
        val scenario = PlaceOrderScenario(compensationRetries = 1)
        val executor = SimulatedStepExecutor<OrderCtx>(mapOf("reservePayment" to InjectedOutcome.TIMEOUT))

        engine(scenario, executor).start(sagaId, scenario.context) shouldBe SagaOutcome.COMPLETED
        eventNames() shouldContainInOrder listOf("SagaStarted", "StepTimedOut", "StepSucceeded")
    }

    @Test
    fun `멱등성 키가 sagaId-stepName-phase-attempt로 포트에 전달된다`() {
        val scenario = PlaceOrderScenario(compensationRetries = 1)
        engine(scenario, SimulatedStepExecutor(emptyMap())).start(sagaId, scenario.context)

        scenario.payment.idempotencyKeys.single() shouldBe "$sagaId:reservePayment:action:1"
    }

    @Test
    fun `터미널 사가의 재실행은 아무 액션도 다시 부르지 않는다 - 결정론적 리플레이`() {
        val scenario = PlaceOrderScenario(compensationRetries = 1)
        engine(scenario, SimulatedStepExecutor(emptyMap())).start(sagaId, scenario.context)

        val explodingExecutor = object : StepExecutor<OrderCtx> {
            override fun executeAction(step: SagaStep<OrderCtx>, context: SagaActionContext<OrderCtx>) =
                throw AssertionError("리플레이가 액션을 재실행했다: ${step.name}")

            override fun executeCompensation(step: SagaStep<OrderCtx>, context: SagaActionContext<OrderCtx>) =
                throw AssertionError("리플레이가 보상을 재실행했다: ${step.name}")
        }
        val replayed = SagaEngine(scenario.definition, store, explodingExecutor, codec).resume(sagaId)

        replayed shouldBe SagaOutcome.COMPLETED
    }

    @Test
    fun `크래시 후 resume - 완료된 step은 건너뛰고 정확한 지점부터 재개한다`() {
        val scenario = PlaceOrderScenario(compensationRetries = 1)
        val crashing = object : StepExecutor<OrderCtx> {
            val delegate = SimulatedStepExecutor<OrderCtx>(emptyMap())
            override fun executeAction(step: SagaStep<OrderCtx>, context: SagaActionContext<OrderCtx>): StepResult {
                if (step.name == "deductStock") throw IllegalStateException("simulated crash")
                return delegate.executeAction(step, context)
            }

            override fun executeCompensation(step: SagaStep<OrderCtx>, context: SagaActionContext<OrderCtx>) =
                delegate.executeCompensation(step, context)
        }

        shouldThrow<IllegalStateException> { engine(scenario, crashing).start(sagaId, scenario.context) }
            .message shouldContain "simulated crash"
        scenario.payment.reserved shouldBe 10_000 // step1은 이미 실행됨

        // 새 엔진 인스턴스가 히스토리(SagaStarted의 컨텍스트 포함)만으로 재개
        val resumed = engine(scenario, SimulatedStepExecutor(emptyMap())).resume(sagaId)

        resumed shouldBe SagaOutcome.COMPLETED
        scenario.payment.reserved shouldBe 10_000 // reservePayment가 두 번 실행되지 않았다
        scenario.stock.deducted shouldBe 1
    }

    private fun eventNames() = store.readStream(sagaId).map { it.event::class.simpleName!! }
}
