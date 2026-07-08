package io.chronos.saga

import io.chronos.core.event.AggregateId
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

/** [times] = 총 시도 횟수 (1이면 재시도 없음). */
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

/**
 * 액션/보상에 전달되는 실행 문맥. [idempotencyKey]는 sagaId + stepName + phase + attempt로,
 * 포트 구현이 재시도 중복 호출을 흡수하는 데 쓴다.
 */
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

/** 한 번의 액션/보상 시도 결과. */
sealed interface StepResult {
    data object Succeeded : StepResult
    data class Failed(val reason: String) : StepResult
    data object TimedOut : StepResult
}

/**
 * 액션 실행 시맨틱의 봉합선. 실제 실행기는 타임아웃을 실제로 걸고,
 * 모델 체커의 시뮬레이션 실행기는 결과를 주입한다. 리플레이 시 mock으로 대체되는 지점이 여기다.
 */
interface StepExecutor<C> {
    fun executeAction(step: SagaStep<C>, context: SagaActionContext<C>): StepResult
    fun executeCompensation(step: SagaStep<C>, context: SagaActionContext<C>): StepResult
}
