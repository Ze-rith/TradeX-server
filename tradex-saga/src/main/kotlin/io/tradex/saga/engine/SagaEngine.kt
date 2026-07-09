package io.tradex.saga.engine

import io.tradex.core.event.AggregateId
import io.tradex.core.store.EventStore
import io.tradex.saga.SagaActionContext
import io.tradex.saga.SagaDefinition
import io.tradex.saga.SagaPhase
import io.tradex.saga.SagaStep
import io.tradex.saga.StepExecutor
import io.tradex.saga.StepResult
import java.time.Clock
import kotlin.time.Duration

class SagaEngine<C>(
    private val definition: SagaDefinition<C>,
    private val store: EventStore,
    private val executor: StepExecutor<C>,
    private val codec: SagaContextCodec<C>,
    private val clock: Clock = Clock.systemUTC(),
    private val sleeper: (Duration) -> Unit = { if (it.isPositive()) Thread.sleep(it.inWholeMilliseconds) },
) {
    fun start(sagaId: AggregateId, context: C): SagaOutcome = run(sagaId, context)

    fun resume(sagaId: AggregateId): SagaOutcome = run(sagaId, null)

    fun replayState(sagaId: AggregateId): SagaReplayState =
        SagaReplayState.from(store.readStream(sagaId).map { it.event as SagaEvent })

    private fun run(sagaId: AggregateId, providedContext: C?): SagaOutcome {
        val session = Session(sagaId)
        val state = SagaReplayState.from(session.history)
        state.outcome?.let { return it }

        val context: C = when (val started = state.started) {
            null -> {
                val ctx = requireNotNull(providedContext) { "새 사가에는 컨텍스트가 필요하다: $sagaId" }
                session.append(SagaStarted(sagaId, clock.instant(), definition.name, codec.encode(ctx)))
                ctx
            }
            else -> providedContext ?: codec.decode(started.contextJson)
        }

        if (state.compensating) {
            return compensate(session, context, state.succeededSteps, state)
        }
        return forward(session, context, state)
    }

    private fun forward(session: Session, context: C, state: SagaReplayState): SagaOutcome {
        val succeeded = state.succeededSteps.toMutableList()

        for (step in definition.steps) {
            if (step.name in succeeded) continue

            var attempt = (state.actionAttempts[step.name] ?: 0) + 1
            var stepSucceeded = false
            while (attempt <= step.retry.times) {
                if (attempt > 1) sleeper(step.retry.delayBefore(attempt))
                val actionContext = SagaActionContext(context, session.sagaId, step.name, attempt, SagaPhase.ACTION)
                when (val result = executor.executeAction(step, actionContext)) {
                    is StepResult.Succeeded -> {
                        session.append(StepSucceeded(session.sagaId, clock.instant(), definition.name, step.name, attempt))
                        stepSucceeded = true
                    }
                    is StepResult.Failed ->
                        session.append(StepFailed(session.sagaId, clock.instant(), definition.name, step.name, attempt, result.reason))
                    is StepResult.TimedOut ->
                        session.append(StepTimedOut(session.sagaId, clock.instant(), definition.name, step.name, attempt))
                }
                if (stepSucceeded) break
                attempt++
            }

            if (!stepSucceeded) {
                session.append(CompensationStarted(session.sagaId, clock.instant(), definition.name, fromStepName = step.name))
                return compensate(session, context, succeeded, state)
            }
            succeeded += step.name
        }

        session.append(SagaCompleted(session.sagaId, clock.instant(), definition.name))
        return SagaOutcome.COMPLETED
    }

    private fun compensate(
        session: Session,
        context: C,
        succeededSteps: List<String>,
        state: SagaReplayState,
    ): SagaOutcome {
        for (stepName in succeededSteps.reversed()) {
            if (stepName in state.compensatedSteps) continue
            val step = definition.steps.first { it.name == stepName }

            if (step.compensate == null) {
                session.append(CompensationSucceeded(session.sagaId, clock.instant(), definition.name, stepName, attempt = 1))
                continue
            }

            var attempt = (state.compensationAttempts[stepName] ?: 0) + 1
            var compensated = false
            while (attempt <= step.compensationRetry.times) {
                if (attempt > 1) sleeper(step.compensationRetry.delayBefore(attempt))
                val actionContext = SagaActionContext(context, session.sagaId, stepName, attempt, SagaPhase.COMPENSATE)
                when (val result = executor.executeCompensation(step, actionContext)) {
                    is StepResult.Succeeded -> {
                        session.append(CompensationSucceeded(session.sagaId, clock.instant(), definition.name, stepName, attempt))
                        compensated = true
                    }
                    is StepResult.Failed ->
                        session.append(CompensationFailed(session.sagaId, clock.instant(), definition.name, stepName, attempt, result.reason))
                    is StepResult.TimedOut ->
                        session.append(CompensationFailed(session.sagaId, clock.instant(), definition.name, stepName, attempt, "timeout"))
                }
                if (compensated) break
                attempt++
            }

            if (!compensated) {

                return SagaOutcome.STUCK
            }
        }

        session.append(SagaCompensated(session.sagaId, clock.instant(), definition.name))
        return SagaOutcome.COMPENSATED
    }

    private inner class Session(val sagaId: AggregateId) {
        val history: List<SagaEvent> = store.readStream(sagaId).map { it.event as SagaEvent }
        private var seqNo: Long = history.size.toLong()

        fun append(event: SagaEvent) {
            store.append(SAGA_AGGREGATE_TYPE, sagaId, seqNo, listOf(event))
            seqNo += 1
        }
    }

    companion object {
        const val SAGA_AGGREGATE_TYPE = "Saga"
    }
}
