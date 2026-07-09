package io.tradex.core.snapshot

import io.tradex.core.event.AggregateId
import java.time.Instant

data class Snapshot<out S>(
    val aggregateId: AggregateId,
    val seqNo: Long,
    val state: S,
    val takenAt: Instant,
)

interface SnapshotStore<S> {
    fun save(snapshot: Snapshot<S>)
    fun load(aggregateId: AggregateId): Snapshot<S>?
}
