package io.chronos.saga.testkit

import io.chronos.saga.SagaActionContext
import io.chronos.saga.SagaStep
import io.chronos.saga.StepExecutor
import io.chronos.saga.StepResult

/**
 * 모델 체커가 step에 주입하는 결함의 종류.
 *
 * - [FAIL]: 영구 실패 — 모든 재시도가 실패한다 (비즈니스 거절 등).
 * - [TIMEOUT]: 일시 장애 — 첫 시도는 타임아웃, 재시도(attempt ≥ 2)가 있으면 성공한다.
 * - [COMPENSATION_TIMEOUT]: 액션은 성공하지만 보상의 첫 시도가 타임아웃. 보상 재시도가
 *   설정돼 있어야만 회복된다 — "보상 도중 타임아웃 → 재시도 소진" 반례의 원료.
 */
enum class InjectedOutcome { SUCCESS, FAIL, TIMEOUT, COMPENSATION_TIMEOUT }

/**
 * 주입표대로 결과를 내는 결정론적 실행기. 실제 시간·스레드를 쓰지 않는다.
 * 성공 경로에서는 진짜 액션/보상 람다를 호출해 fake 포트에 부수효과가 남게 한다
 * (불변식이 포트 상태를 검사할 수 있도록).
 */
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
