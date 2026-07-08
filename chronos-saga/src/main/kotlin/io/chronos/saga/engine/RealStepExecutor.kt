package io.chronos.saga.engine

import io.chronos.saga.SagaActionContext
import io.chronos.saga.SagaStep
import io.chronos.saga.StepExecutor
import io.chronos.saga.StepResult
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration

/** 가상 스레드에서 실제 타임아웃을 걸고 액션을 실행한다. */
class RealStepExecutor<C>(
    private val pool: ExecutorService = Executors.newVirtualThreadPerTaskExecutor(),
) : StepExecutor<C> {
    override fun executeAction(step: SagaStep<C>, context: SagaActionContext<C>): StepResult =
        runWithTimeout(step.timeout) { step.action(context) }

    override fun executeCompensation(step: SagaStep<C>, context: SagaActionContext<C>): StepResult =
        runWithTimeout(step.timeout) { requireNotNull(step.compensate)(context) }

    private fun runWithTimeout(timeout: Duration, block: () -> Unit): StepResult {
        val future = pool.submit(block)
        return try {
            future.get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            StepResult.Succeeded
        } catch (e: TimeoutException) {
            future.cancel(true)
            StepResult.TimedOut
        } catch (e: ExecutionException) {
            StepResult.Failed(e.cause?.message ?: e.cause?.let { it::class.simpleName } ?: "unknown error")
        }
    }
}
