package io.chronos.core.query

import io.chronos.core.aggregate.Aggregate
import io.chronos.core.event.AggregateId
import io.chronos.core.event.DomainEvent
import io.chronos.core.event.EventId
import io.chronos.core.event.EventRecord
import io.chronos.core.store.EventStore
import java.time.Instant

/**
 * 이벤트 리플레이로 상태를 복원하는 bi-temporal 조회기.
 *
 * 정정 치환 규칙 (DECISIONS.md D5): 정정 이벤트는 독립 엔트리가 아니라
 * 원본의 스트림 위치에서 원본 페이로드를 대체한다. 정정의 정정은 체인을 끝까지 따른다.
 */
class AggregateRepository<S, E : DomainEvent>(
    private val aggregate: Aggregate<S, E>,
    private val store: EventStore,
) {
    /** 현재 지식 전체 기준 최신 상태 (정정 반영). */
    fun currentState(id: AggregateId): S = replay(effective(store.readStream(id)))

    /** 정정이 반영된 "그 시점(valid time)의 진실". */
    fun stateAsOf(id: AggregateId, validTime: Instant): S =
        replay(effective(store.readStream(id)).filter { !it.event.validTime.isAfter(validTime) })

    /** "그 당시(transaction time) 시스템이 알던 모습" — 이후에 기록된 정정은 미반영. */
    fun stateAsAt(id: AggregateId, transactionTime: Instant): S =
        replay(effective(store.readStreamAsAt(id, transactionTime)))

    fun currentSeqNo(id: AggregateId): Long = store.readStream(id).lastOrNull()?.seqNo ?: 0L

    /**
     * 정정을 원본 위치로 접어 넣은 "유효 스트림".
     * 반환 레코드의 event는 최종 정정본, 위치(seqNo)는 원본의 것이다.
     */
    private fun effective(records: List<EventRecord<DomainEvent>>): List<EventRecord<DomainEvent>> {
        val latestCorrectionByTarget = HashMap<EventId, DomainEvent>()
        for (record in records) {
            record.event.correctionOf?.let { latestCorrectionByTarget[it] = record.event }
        }
        return records
            .filter { it.event.correctionOf == null }
            .map { original ->
                var resolved = original.event
                while (true) {
                    resolved = latestCorrectionByTarget[resolved.eventId] ?: break
                }
                if (resolved === original.event) original else original.copy(event = resolved)
            }
    }

    private fun replay(records: List<EventRecord<DomainEvent>>): S {
        var state = aggregate.initial
        for (record in records) {
            @Suppress("UNCHECKED_CAST")
            state = aggregate.evolve(state, record.event as E)
        }
        return state
    }
}
