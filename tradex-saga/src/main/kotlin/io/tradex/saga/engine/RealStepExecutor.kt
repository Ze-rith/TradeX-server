package io.tradex.saga.engine

import io.tradex.saga.SagaActionContext
import io.tradex.saga.SagaStep
import io.tradex.saga.StepExecutor
import io.tradex.saga.StepResult
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration

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
