package io.tradex.cell

import io.tradex.core.event.AggregateId
import io.tradex.core.store.StreamImporter

enum class MigrationPhase { COPY_STREAM, CATCH_UP_OFFSETS, SWITCH_ROUTING, TOMBSTONE_SOURCE }

data class MigrationReport(
    val aggregateId: AggregateId,
    val fromCellId: Int,
    val toCellId: Int,
    val copiedEvents: Int,
)

class CellMigrator(private val fabric: CellFabric) {
    fun migrate(
        aggregateId: AggregateId,
        targetCellId: Int,
        onPhase: (MigrationPhase) -> Unit = {},
    ): MigrationReport {
        val sourceCell = fabric.cellFor(aggregateId)
        val targetCell = fabric.cells[targetCellId]
            ?: throw IllegalArgumentException("존재하지 않는 cell: $targetCellId")
        if (sourceCell.cellId == targetCellId) {
            return MigrationReport(aggregateId, sourceCell.cellId, targetCellId, copiedEvents = 0)
        }
        targetCell.ensureUp()
        val importer = targetCell.store as? StreamImporter
            ?: throw IllegalStateException("target cell ${targetCellId}의 스토어가 StreamImporter를 지원하지 않음")

        fabric.beginMigration(aggregateId)
        try {

            onPhase(MigrationPhase.COPY_STREAM)
            val stream = sourceCell.readStream(aggregateId)
            if (stream.isNotEmpty()) importer.importStream(stream)

            onPhase(MigrationPhase.CATCH_UP_OFFSETS)
            targetCell.projectors.forEach { projector -> while (projector.catchUpOnce() > 0) Unit }

            onPhase(MigrationPhase.SWITCH_ROUTING)
            fabric.switchRoute(aggregateId, targetCellId)

            onPhase(MigrationPhase.TOMBSTONE_SOURCE)
            sourceCell.tombstone(aggregateId)

            return MigrationReport(aggregateId, sourceCell.cellId, targetCellId, copiedEvents = stream.size)
        } finally {
            fabric.endMigration(aggregateId)
        }
    }
}
