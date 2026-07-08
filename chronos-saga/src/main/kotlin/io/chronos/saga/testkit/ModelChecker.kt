package io.chronos.saga.testkit

import io.chronos.core.event.AggregateId
import io.chronos.core.store.InMemoryEventStore
import io.chronos.saga.SagaDefinition
import io.chronos.saga.engine.SagaCompensated
import io.chronos.saga.engine.SagaCompleted
import io.chronos.saga.engine.SagaContextCodec
import io.chronos.saga.engine.SagaEngine
import io.chronos.saga.engine.SagaEvent
import io.chronos.saga.engine.SagaOutcome
import io.chronos.saga.engine.SagaStarted
import io.chronos.saga.engine.StepFailed
import io.chronos.saga.engine.StepSucceeded
import io.chronos.saga.engine.StepTimedOut
import io.chronos.saga.engine.CompensationFailed
import io.chronos.saga.engine.CompensationStarted
import io.chronos.saga.engine.CompensationSucceeded

/** 탐색 대상: 사가 정의 + 컨텍스트 + (불변식이 들여다볼) fake 포트들. 경로마다 새로 생성된다. */
interface SagaScenario<C> {
    val definition: SagaDefinition<C>
    val context: C
}

class SagaRunResult<S : SagaScenario<*>>(
    val scenario: S,
    val injected: Map<String, InjectedOutcome>,
    val outcome: SagaOutcome,
    val events: List<SagaEvent>,
) {
    val isTerminal: Boolean get() = outcome != SagaOutcome.STUCK
}

data class Invariant<S : SagaScenario<*>>(
    val name: String,
    val predicate: (SagaRunResult<S>) -> Boolean,
)

fun <S : SagaScenario<*>> invariant(name: String, predicate: (SagaRunResult<S>) -> Boolean) =
    Invariant(name, predicate)

class StateSpaceExceededException(steps: Int, maxSteps: Int) : RuntimeException(
    "상태공간 상한 초과: step ${steps}개 → ${InjectedOutcome.entries.size}^$steps 경로. " +
        "모델 체커는 step ≤ $maxSteps 사가만 지원한다. 사가를 분해하라.",
)

/**
 * 사가 모델 체커: 각 step에 {성공, 실패, 타임아웃, 보상타임아웃}을 주입하는
 * 모든 조합(4^n)을 결정론적으로 실행하고 불변식을 평가한다.
 * 위반 발견 시 사람이 읽을 수 있는 반례 경로를 만든다.
 */
class ModelChecker<C, S : SagaScenario<C>>(
    private val scenarioFactory: () -> S,
    private val maxSteps: Int = 6,
) {
    fun check(vararg invariants: Invariant<S>): CheckResult<S> {
        val stepNames = scenarioFactory().definition.steps.map { it.name }
        if (stepNames.size > maxSteps) throw StateSpaceExceededException(stepNames.size, maxSteps)

        val violations = mutableListOf<Violation<S>>()
        var paths = 0
        for (assignment in allAssignments(stepNames)) {
            paths++
            val result = runPath(assignment)
            for (inv in invariants) {
                if (!inv.predicate(result)) violations += Violation(inv.name, result)
            }
        }
        return CheckResult(violations, paths)
    }

    private fun runPath(assignment: Map<String, InjectedOutcome>): SagaRunResult<S> {
        val scenario = scenarioFactory()
        val store = InMemoryEventStore()
        val engine = SagaEngine(
            definition = scenario.definition,
            store = store,
            executor = SimulatedStepExecutor(assignment),
            codec = PassthroughCodec(),
            sleeper = {}, // 시뮬레이션에서는 백오프 대기 없음
        )
        val sagaId = AggregateId.new()
        val outcome = engine.start(sagaId, scenario.context)
        val events = store.readStream(sagaId).map { it.event as SagaEvent }
        return SagaRunResult(scenario, assignment, outcome, events)
    }

    private fun allAssignments(stepNames: List<String>): Sequence<Map<String, InjectedOutcome>> = sequence {
        val outcomes = InjectedOutcome.entries
        val indices = IntArray(stepNames.size)
        while (true) {
            yield(stepNames.withIndex().associate { (i, name) -> name to outcomes[indices[i]] })
            var position = 0
            while (position < indices.size) {
                indices[position]++
                if (indices[position] < outcomes.size) break
                indices[position] = 0
                position++
            }
            if (position == indices.size) break
        }
    }

    private class PassthroughCodec<C> : SagaContextCodec<C> {
        override fun encode(context: C): String = "{}"
        override fun decode(json: String): C = error("모델 체커 경로에서는 컨텍스트를 복원하지 않는다")
    }
}

data class Violation<S : SagaScenario<*>>(val invariantName: String, val result: SagaRunResult<S>)

class CheckResult<S : SagaScenario<*>>(
    val violations: List<Violation<S>>,
    val pathsExplored: Int,
) {
    fun assertNoViolations() {
        if (violations.isEmpty()) return
        val sample = violations.take(3).joinToString("\n\n") { prettyCounterexample(it) }
        throw AssertionError("모델 체커가 ${violations.size}건의 불변식 위반을 발견 (탐색 경로 $pathsExplored):\n\n$sample")
    }

    companion object {
        fun prettyCounterexample(violation: Violation<*>): String = buildString {
            val r = violation.result
            appendLine("반례 — 불변식 위반: \"${violation.invariantName}\"")
            appendLine("주입된 결함: " + r.injected.entries.joinToString(", ") { "${it.key}=${it.value}" })
            appendLine("이벤트 경로:")
            r.events.forEachIndexed { i, e -> appendLine("  ${i + 1}. ${pretty(e)}") }
            append("최종 결과: ${r.outcome}${if (!r.isTerminal) " (터미널 아님 — 운영 개입 필요)" else ""}")
        }

        private fun pretty(event: SagaEvent): String = when (event) {
            is SagaStarted -> "SagaStarted(${event.sagaName})"
            is StepSucceeded -> "StepSucceeded(${event.stepName} #${event.attempt})"
            is StepFailed -> "StepFailed(${event.stepName} #${event.attempt}: ${event.reason})"
            is StepTimedOut -> "StepTimedOut(${event.stepName} #${event.attempt})"
            is CompensationStarted -> "CompensationStarted(from=${event.fromStepName})"
            is CompensationSucceeded -> "CompensationSucceeded(${event.stepName} #${event.attempt})"
            is CompensationFailed -> "CompensationFailed(${event.stepName} #${event.attempt}: ${event.reason})"
            is SagaCompleted -> "SagaCompleted"
            is SagaCompensated -> "SagaCompensated"
        }
    }
}
