package io.chronos.core.snapshot

import io.chronos.core.event.AggregateId
import java.util.concurrent.ConcurrentHashMap

class InMemorySnapshotStore<S> : SnapshotStore<S> {
    private val snapshots = ConcurrentHashMap<AggregateId, Snapshot<S>>()

    override fun save(snapshot: Snapshot<S>) {
        snapshots[snapshot.aggregateId] = snapshot
    }

    override fun load(aggregateId: AggregateId): Snapshot<S>? = snapshots[aggregateId]
}
