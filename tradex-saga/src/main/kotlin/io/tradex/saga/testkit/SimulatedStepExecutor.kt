package io.tradex.saga.testkit

import io.tradex.saga.SagaActionContext
import io.tradex.saga.SagaStep
import io.tradex.saga.StepExecutor
import io.tradex.saga.StepResult

enum class InjectedOutcome { SUCCESS, FAIL, TIMEOUT, COMPENSATION_TIMEOUT }

class SimulatedStepExecutor<C>(
    private val outcomes: Map<String, InjectedOutcome>,
) : StepExecutor<C> {
    override fun executeAction(step: SagaStep<C>, context: SagaActionContext<C>): StepResult =
        when (outcomes[step.name] ?: InjectedOutcome.SUCCESS) {
            InjectedOutcome.SUCCESS, InjectedOutcome.COMPENSATION_TIMEOUT -> {
                step.action(context)
                StepResult.Succeeded
            }
            InjectedOutcome.FAIL -> StepResult.Failed("injected permanent failure")
            InjectedOutcome.TIMEOUT ->
                if (context.attempt == 1) {
                    StepResult.TimedOut
                } else {
                    step.action(context)
                    StepResult.Succeeded
                }
        }

    override fun executeCompensation(step: SagaStep<C>, context: SagaActionContext<C>): StepResult =
        when (outcomes[step.name]) {
            InjectedOutcome.COMPENSATION_TIMEOUT ->
                if (context.attempt == 1) {
                    StepResult.TimedOut
                } else {
                    requireNotNull(step.compensate)(context)
                    StepResult.Succeeded
                }
            else -> {
                requireNotNull(step.compensate)(context)
                StepResult.Succeeded
            }
        }
}
