package io.chronos.core.aggregate

import io.chronos.core.event.DomainEvent

/**
 * 이벤트 소싱 애그리게잇 정의.
 *
 * [evolve]는 순수 함수여야 한다: 같은 (state, event)에 항상 같은 결과,
 * 부수효과·현재 시각·난수 접근 금지. 상태는 항상 이벤트 리플레이로 복원된다.
 */
interface Aggregate<S, E : DomainEvent> {
    val type: String
    val initial: S
    fun evolve(state: S, event: E): S
}
