package io.tradex.cell

import io.tradex.core.event.AggregateId
import io.tradex.core.event.DomainEvent
import io.tradex.core.event.EventRecord
import io.tradex.core.store.InMemoryEventStore
import java.util.concurrent.ConcurrentHashMap

class CellFabric(
    cellCount: Int = 3,
    virtualNodes: Int = 128,
    cellFactory: (Int) -> Cell = { id -> Cell(id, InMemoryEventStore(cellId = id)) },
) {
    val cells: Map<Int, Cell> = (0 until cellCount).associateWith(cellFactory)
    private val ring = ConsistentHashRing(cells.keys, virtualNodes)

    private val routeOverrides = ConcurrentHashMap<AggregateId, Int>()

    private val migrating = ConcurrentHashMap.newKeySet<AggregateId>()

    fun cellIdFor(aggregateId: AggregateId): Int = routeOverrides[aggregateId] ?: ring.route(aggregateId)

    fun cellFor(aggregateId: AggregateId): Cell {
        val cell = cells.getValue(cellIdFor(aggregateId))
        cell.ensureUp()
        return cell
    }

    fun append(
        aggregateType: String,
        aggregateId: AggregateId,
        expectedSeqNo: Long,
        events: List<DomainEvent>,
    ): List<EventRecord<DomainEvent>> {
        if (aggregateId in migrating) throw MigrationInProgressException(aggregateId)
        return cellFor(aggregateId).append(aggregateType, aggregateId, expectedSeqNo, events)
    }

    fun readStream(aggregateId: AggregateId): List<EventRecord<DomainEvent>> =
        cellFor(aggregateId).readStream(aggregateId)

    fun down(cellId: Int) = cells.getValue(cellId).markDown()
    fun up(cellId: Int) = cells.getValue(cellId).markUp()

    internal fun beginMigration(aggregateId: AggregateId) {
        if (!migrating.add(aggregateId)) throw MigrationInProgressException(aggregateId)
    }

    internal fun endMigration(aggregateId: AggregateId) {
        migrating.remove(aggregateId)
    }

    internal fun switchRoute(aggregateId: AggregateId, targetCellId: Int) {
        routeOverrides[aggregateId] = targetCellId
    }
}

class MigrationInProgressException(aggregateId: AggregateId) :
    RuntimeException("aggregate $aggregateId 마이그레이션 진행 중 — 쓰기 거절, 재시도하라")
