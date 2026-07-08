package io.chronos.cell

import io.chronos.core.event.AggregateId
import io.chronos.core.event.DomainEvent
import io.chronos.core.event.EventRecord
import io.chronos.core.store.EventStore
import io.chronos.router.Projector
import io.chronos.saga.SagaDefinition
import io.chronos.saga.StepExecutor
import io.chronos.saga.engine.SagaContextCodec
import io.chronos.saga.engine.SagaEngine
import java.util.concurrent.ConcurrentHashMap

/**
 * Cell = {이벤트 스토어 파티션 + 프로젝션 세트 + 사가 엔진} 격리 단위.
 * 셀 하나가 죽어도 다른 셀의 요청은 영향을 받지 않는다 (blast radius 격리).
 */
class Cell(
    val cellId: Int,
    val store: EventStore,
    val projectors: List<Projector> = emptyList(),
) {
    @Volatile
    var up: Boolean = true
        private set

    private val tombstoned = ConcurrentHashMap.newKeySet<AggregateId>()

    /** 이 셀 파티션에 바인딩된 사가 엔진 — 사가 이벤트도 같은 파티션에 기록된다. */
    fun <C> sagaEngine(
        definition: SagaDefinition<C>,
        executor: StepExecutor<C>,
        codec: SagaContextCodec<C>,
    ): SagaEngine<C> = SagaEngine(definition, store, executor, codec)

    fun markDown() {
        up = false
    }

    fun markUp() {
        up = true
    }

    /** 마이그레이션 완료 후 소스 셀에 남는 봉인 표식. 데이터는 보존되나 접근은 거절된다. */
    fun tombstone(aggregateId: AggregateId) {
        tombstoned += aggregateId
    }

    fun isTombstoned(aggregateId: AggregateId): Boolean = aggregateId in tombstoned

    fun readStream(aggregateId: AggregateId): List<EventRecord<DomainEvent>> {
        ensureUp()
        if (isTombstoned(aggregateId)) throw TombstonedAggregateException(aggregateId, cellId)
        return store.readStream(aggregateId)
    }

    fun append(
        aggregateType: String,
        aggregateId: AggregateId,
        expectedSeqNo: Long,
        events: List<DomainEvent>,
    ): List<EventRecord<DomainEvent>> {
        ensureUp()
        if (isTombstoned(aggregateId)) throw TombstonedAggregateException(aggregateId, cellId)
        return store.append(aggregateType, aggregateId, expectedSeqNo, events)
    }

    internal fun ensureUp() {
        if (!up) throw CellDownException(cellId)
    }
}

class CellDownException(cellId: Int) : RuntimeException("cell $cellId is down")

class TombstonedAggregateException(aggregateId: AggregateId, cellId: Int) :
    RuntimeException("aggregate ${aggregateId}는 cell ${cellId}에서 다른 셀로 이관됨 (tombstone)")
