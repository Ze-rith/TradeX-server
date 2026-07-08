package io.chronos.saga.dsl

import io.chronos.saga.Backoff
import io.chronos.saga.RetryPolicy
import io.chronos.saga.SagaActionContext
import io.chronos.saga.SagaDefinition
import io.chronos.saga.SagaStep
import io.chronos.saga.fixed
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * ```
 * val placeOrder = saga<OrderCtx>("PlaceOrder") {
 *     step("reservePayment") {
 *         action { paymentPort.reserve(it.idempotencyKey, it.ctx.amount) }
 *         compensate { paymentPort.release(it.idempotencyKey, it.ctx.amount) }
 *         timeout(5.seconds)
 *         retry(times = 3, backoff = exponential(200.milliseconds))
 *         compensationRetry(times = 3)
 *     }
 * }
 * ```
 */
fun <C> saga(name: String, block: SagaBuilder<C>.() -> Unit): SagaDefinition<C> =
    SagaBuilder<C>(name).apply(block).build()

@DslMarker
annotation class SagaDslMarker

@SagaDslMarker
class SagaBuilder<C>(private val name: String) {
    private val steps = mutableListOf<SagaStep<C>>()

    fun step(name: String, block: StepBuilder<C>.() -> Unit) {
        steps += StepBuilder<C>(name).apply(block).build()
    }

    fun build(): SagaDefinition<C> = SagaDefinition(name, steps.toList())
}

@SagaDslMarker
class StepBuilder<C>(private val name: String) {
    private var action: ((SagaActionContext<C>) -> Unit)? = null
    private var compensate: ((SagaActionContext<C>) -> Unit)? = null
    private var timeout: Duration = 5.seconds
    private var retry: RetryPolicy = RetryPolicy(times = 1, backoff = fixed())
    private var compensationRetry: RetryPolicy = RetryPolicy(times = 1, backoff = fixed())

    fun action(block: (SagaActionContext<C>) -> Unit) {
        action = block
    }

    fun compensate(block: (SagaActionContext<C>) -> Unit) {
        compensate = block
    }

    fun timeout(duration: Duration) {
        timeout = duration
    }

    fun retry(times: Int, backoff: Backoff = fixed()) {
        retry = RetryPolicy(times, backoff)
    }

    fun compensationRetry(times: Int, backoff: Backoff = fixed()) {
        compensationRetry = RetryPolicy(times, backoff)
    }

    fun build(): SagaStep<C> = SagaStep(
        name = name,
        action = requireNotNull(action) { "step '$name' has no action" },
        compensate = compensate,
        timeout = timeout,
        retry = retry,
        compensationRetry = compensationRetry,
    )
}
