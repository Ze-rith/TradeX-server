package io.chronos.core.query

import io.chronos.core.aggregate.Aggregate
import io.chronos.core.event.AggregateId
import io.chronos.core.event.DomainEvent
import io.chronos.core.snapshot.Snapshot
import io.chronos.core.snapshot.SnapshotStore
import io.chronos.core.store.EventStore
import java.time.Instant

/**
 * 스냅샷으로 리플레이를 가속하는 최신 상태 조회기.
 *
 * 스냅샷은 캐시일 뿐이다. 스냅샷 이후에 기록된 정정이 스냅샷 시점 이전 이벤트를
 * 겨냥하면(소급 정정) 스냅샷은 무효이므로 전체 리플레이로 폴백한다.
 * bi-temporal 조회(asOf/asAt)는 의미상 항상 전체 리플레이 — [AggregateRepository] 사용.
 */
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
            target !in tailEventIds // 스냅샷 구간 이전의 이벤트를 소급 정정
        }
        if (correctsBeforeSnapshot) return fullReplay.currentState(id)

        var state = snapshot.state
        for (record in effectiveStream(tail)) {
            @Suppress("UNCHECKED_CAST")
            state = aggregate.evolve(state, record.event as E)
        }
        return state
    }

    /** 스냅샷 이후 이벤트가 [snapshotEvery]개 이상 쌓였으면 새 스냅샷을 저장한다. */
    fun maybeSnapshot(id: AggregateId, now: Instant) {
        val lastSeqNo = store.readStream(id).lastOrNull()?.seqNo ?: return
        val snapshotSeqNo = snapshots.load(id)?.seqNo ?: 0L
        if (lastSeqNo - snapshotSeqNo >= snapshotEvery) {
            snapshots.save(Snapshot(id, lastSeqNo, fullReplay.currentState(id), now))
        }
    }
}
