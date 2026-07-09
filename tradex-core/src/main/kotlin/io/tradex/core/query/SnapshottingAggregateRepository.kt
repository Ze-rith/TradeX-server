package io.tradex.core.query

import io.tradex.core.aggregate.Aggregate
import io.tradex.core.event.AggregateId
import io.tradex.core.event.DomainEvent
import io.tradex.core.snapshot.Snapshot
import io.tradex.core.snapshot.SnapshotStore
import io.tradex.core.store.EventStore
import java.time.Instant

class SnapshottingAggregateRepository<S, E : DomainEvent>(
    private val aggregate: Aggregate<S, E>,
    private val store: EventStore,
    private val snapshots: SnapshotStore<S>,
    private val snapshotEvery: Int = 100,
) {
    private val fullReplay = AggregateRepository(aggregate, store)

    fun currentState(id: AggregateId): S {
        val snapshot = snapshots.load(id) ?: return fullReplay.currentState(id)
        val tail = store.readStream(id, afterSeqNo = snapshot.seqNo)

        val tailEventIds = tail.map { it.eventId }.toSet()
        val correctsBeforeSnapshot = tail.any { record ->
            val target = record.event.correctionOf ?: return@any false
            target !in tailEventIds
        }
        if (correctsBeforeSnapshot) return fullReplay.currentState(id)

        var state = snapshot.state
        for (record in effectiveStream(tail)) {
            @Suppress("UNCHECKED_CAST")
            state = aggregate.evolve(state, record.event as E)
        }
        return state
    }

    fun maybeSnapshot(id: AggregateId, now: Instant) {
        val lastSeqNo = store.readStream(id).lastOrNull()?.seqNo ?: return
        val snapshotSeqNo = snapshots.load(id)?.seqNo ?: 0L
        if (lastSeqNo - snapshotSeqNo >= snapshotEvery) {
            snapshots.save(Snapshot(id, lastSeqNo, fullReplay.currentState(id), now))
        }
    }
}
