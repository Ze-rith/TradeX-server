package io.tradex.saga

import io.tradex.core.event.AggregateId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SagaDefinition<C>(
    val name: String,
    val steps: List<SagaStep<C>>,
) {
    init {
        require(steps.isNotEmpty()) { "saga '$name' has no steps" }
        require(steps.map { it.name }.toSet().size == steps.size) { "saga '$name' has duplicate step names" }
    }
}

class SagaStep<C>(
    val name: String,
    val action: (SagaActionContext<C>) -> Unit,
    val compensate: ((SagaActionContext<C>) -> Unit)?,
    val timeout: Duration = 5.seconds,
    val retry: RetryPolicy = RetryPolicy(times = 1, backoff = Backoff.Fixed(Duration.ZERO)),
    val compensationRetry: RetryPolicy = RetryPolicy(times = 1, backoff = Backoff.Fixed(Duration.ZERO)),
)

data class RetryPolicy(val times: Int, val backoff: Backoff) {
    init {
        require(times >= 1) { "retry times must be >= 1" }
    }

    fun delayBefore(attempt: Int): Duration = when (backoff) {
        is Backoff.Fixed -> backoff.delay
        is Backoff.Exponential -> backoff.base * (1 shl (attempt - 2)).coerceAtLeast(1)
    }
}

sealed interface Backoff {
    data class Fixed(val delay: Duration) : Backoff
    data class Exponential(val base: Duration) : Backoff
}

fun exponential(base: Duration): Backoff = Backoff.Exponential(base)
fun fixed(delay: Duration = 0.milliseconds): Backoff = Backoff.Fixed(delay)

data class SagaActionContext<C>(
    val ctx: C,
    val sagaId: AggregateId,
    val stepName: String,
    val attempt: Int,
    val phase: SagaPhase,
) {
    val idempotencyKey: String get() = "$sagaId:$stepName:${phase.name.lowercase()}:$attempt"
}

enum class SagaPhase { ACTION, COMPENSATE }

sealed interface StepResult {
    data object Succeeded : StepResult
    data class Failed(val reason: String) : StepResult
    data object TimedOut : StepResult
}

interface StepExecutor<C> {
    fun executeAction(step: SagaStep<C>, context: SagaActionContext<C>): StepResult
    fun executeCompensation(step: SagaStep<C>, context: SagaActionContext<C>): StepResult
}
