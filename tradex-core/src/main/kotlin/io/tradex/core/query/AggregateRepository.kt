package io.tradex.core.query

import io.tradex.core.aggregate.Aggregate
import io.tradex.core.event.AggregateId
import io.tradex.core.event.DomainEvent
import io.tradex.core.event.EventRecord
import io.tradex.core.store.EventStore
import java.time.Instant

class AggregateRepository<S, E : DomainEvent>(
    private val aggregate: Aggregate<S, E>,
    private val store: EventStore,
) {

    fun currentState(id: AggregateId): S = replay(effectiveStream(store.readStream(id)))

    fun stateAsOf(id: AggregateId, validTime: Instant): S =
        replay(effectiveStream(store.readStream(id)).filter { !it.event.validTime.isAfter(validTime) })

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
