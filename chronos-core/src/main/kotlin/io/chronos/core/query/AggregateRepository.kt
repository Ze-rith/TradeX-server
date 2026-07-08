package io.chronos.core.query

import io.chronos.core.aggregate.Aggregate
import io.chronos.core.event.AggregateId
import io.chronos.core.event.DomainEvent
import io.chronos.core.event.EventRecord
import io.chronos.core.store.EventStore
import java.time.Instant

/**
 * 이벤트 리플레이로 상태를 복원하는 bi-temporal 조회기. 정정 치환은 [effectiveStream] 참조.
 */
class AggregateRepository<S, E : DomainEvent>(
    private val aggregate: Aggregate<S, E>,
    private val store: EventStore,
) {
    /** 현재 지식 전체 기준 최신 상태 (정정 반영). */
    fun currentState(id: AggregateId): S = replay(effectiveStream(store.readStream(id)))

    /** 정정이 반영된 "그 시점(valid time)의 진실". */
    fun stateAsOf(id: AggregateId, validTime: Instant): S =
        replay(effectiveStream(store.readStream(id)).filter { !it.event.validTime.isAfter(validTime) })

    /** "그 당시(transaction time) 시스템이 알던 모습" — 이후에 기록된 정정은 미반영. */
    fun stateAsAt(id: AggregateId, transactionTime: Instant): S =
        replay(effectiveStream(store.readStreamAsAt(id, transactionTime)))

    fun currentSeqNo(id: AggregateId): Long = store.readStream(id).lastOrNull()?.seqNo ?: 0L

    internal fun replay(records: List<EventRecord<DomainEvent>>): S {
        var state = aggregate.initial
        for (record in records) {
            @Suppress("UNCHECKED_CAST")
            state = aggregate.evolve(state, record.event as E)
        }
        return state
    }
}
