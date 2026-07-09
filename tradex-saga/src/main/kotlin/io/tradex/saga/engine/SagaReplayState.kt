package io.tradex.saga.engine

data class SagaReplayState(
    val started: SagaStarted? = null,
    val succeededSteps: List<String> = emptyList(),
    val compensatedSteps: Set<String> = emptySet(),
    val compensating: Boolean = false,
    val actionAttempts: Map<String, Int> = emptyMap(),
    val compensationAttempts: Map<String, Int> = emptyMap(),
    val outcome: SagaOutcome? = null,
) {
    val isTerminal: Boolean get() = outcome != null

    companion object {
        fun from(events: List<SagaEvent>): SagaReplayState = events.fold(SagaReplayState()) { state, event ->
            when (event) {
                is SagaStarted -> state.copy(started = event)
                is StepSucceeded -> state.copy(
                    succeededSteps = state.succeededSteps + event.stepName,
                    actionAttempts = state.actionAttempts + (event.stepName to event.attempt),
                )
                is StepFailed -> state.copy(actionAttempts = state.actionAttempts + (event.stepName to event.attempt))
                is StepTimedOut -> state.copy(actionAttempts = state.actionAttempts + (event.stepName to event.attempt))
                is CompensationStarted -> state.copy(compensating = true)
                is CompensationSucceeded -> state.copy(
                    compensatedSteps = state.compensatedSteps + event.stepName,
                    compensationAttempts = state.compensationAttempts + (event.stepName to event.attempt),
                )
                is CompensationFailed -> state.copy(
                    compensationAttempts = state.compensationAttempts + (event.stepName to event.attempt),
                )
                is SagaCompleted -> state.copy(outcome = SagaOutcome.COMPLETED)
                is SagaCompensated -> state.copy(outcome = SagaOutcome.COMPENSATED)
            }
        }
    }
}

enum class SagaOutcome {
    COMPLETED,
    COMPENSATED,

    STUCK,
}
