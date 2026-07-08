package io.chronos.core.snapshot

import io.chronos.core.event.AggregateId
import java.time.Instant

/**
 * 리플레이 가속용 스냅샷 포트. 구현은 M2(Postgres)에서.
 * 스냅샷은 캐시일 뿐 진실은 항상 이벤트 스트림이다.
 */
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
