package io.tradex.core.snapshot

import io.tradex.core.event.AggregateId
import java.util.concurrent.ConcurrentHashMap

class InMemorySnapshotStore<S> : SnapshotStore<S> {
    private val snapshots = ConcurrentHashMap<AggregateId, Snapshot<S>>()

    override fun save(snapshot: Snapshot<S>) {
        snapshots[snapshot.aggregateId] = snapshot
    }

    override fun load(aggregateId: AggregateId): Snapshot<S>? = snapshots[aggregateId]
}
