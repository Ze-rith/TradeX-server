package io.tradex.cell

import io.tradex.core.event.AggregateId
import io.tradex.core.event.DomainEvent
import io.tradex.core.event.EventRecord
import io.tradex.core.store.EventStore
import io.tradex.router.Projector
import io.tradex.saga.SagaDefinition
import io.tradex.saga.StepExecutor
import io.tradex.saga.engine.SagaContextCodec
import io.tradex.saga.engine.SagaEngine
import java.util.concurrent.ConcurrentHashMap

class Cell(
    val cellId: Int,
    val store: EventStore,
    val projectors: List<Projector> = emptyList(),
) {
    @Volatile
    var up: Boolean = true
        private set

    private val tombstoned = ConcurrentHashMap.newKeySet<AggregateId>()

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
