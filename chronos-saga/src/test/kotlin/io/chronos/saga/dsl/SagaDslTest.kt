package io.chronos.saga.dsl

import io.chronos.saga.Backoff
import io.chronos.saga.OrderCtx
import io.chronos.saga.PlaceOrderScenario
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Test

class SagaDslTest {
    @Test
    fun `빌더가 step 순서와 정책을 보존한다`() {
        val definition = PlaceOrderScenario(compensationRetries = 3).definition

        definition.name shouldBe "PlaceOrder"
        definition.steps.map { it.name } shouldBe listOf("reservePayment", "deductStock", "createShipment")

        val payment = definition.steps[0]
        payment.timeout shouldBe 5.seconds
        payment.retry.times shouldBe 3
        payment.retry.backoff.shouldBeInstanceOf<Backoff.Exponential>().base shouldBe 200.milliseconds
        payment.compensationRetry.times shouldBe 3

        definition.steps[2].compensate shouldBe null
    }

    @Test
    fun `지수 백오프는 attempt마다 두 배가 된다`() {
        val retry = io.chronos.saga.RetryPolicy(times = 4, backoff = Backoff.Exponential(200.milliseconds))
        retry.delayBefore(2) shouldBe 200.milliseconds
        retry.delayBefore(3) shouldBe 400.milliseconds
        retry.delayBefore(4) shouldBe 800.milliseconds
    }

    @Test
    fun `action 없는 step은 정의 시점에 거부된다`() {
        shouldThrow<IllegalArgumentException> {
            saga<OrderCtx>("Broken") { step("noAction") { compensate {} } }
        }
    }

    @Test
    fun `중복 step 이름은 거부된다`() {
        shouldThrow<IllegalArgumentException> {
            saga<OrderCtx>("Dup") {
                step("a") { action {} }
                step("a") { action {} }
            }
        }
    }
}
