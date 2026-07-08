package io.chronos.cell

import io.chronos.core.event.AggregateId
import io.chronos.core.store.StreamImporter

enum class MigrationPhase { COPY_STREAM, CATCH_UP_OFFSETS, SWITCH_ROUTING, TOMBSTONE_SOURCE }

data class MigrationReport(
    val aggregateId: AggregateId,
    val fromCellId: Int,
    val toCellId: Int,
    val copiedEvents: Int,
)

/**
 * 셀 마이그레이션: ① 이벤트 스트림 복사 → ② 오프셋 캐치업 → ③ 라우팅 스위치 → ④ 소스 tombstone.
 *
 * 상태가 아니라 **이벤트가 진실**이므로 복사 대상은 스트림뿐이다 — 대상 셀의 상태는
 * 리플레이로, 프로젝션은 캐치업으로 저절로 복원된다. 진행 중 쓰기는 거절(D10).
 */
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

        fabric.beginMigration(aggregateId) // 이 순간부터 쓰기 거절
        try {
            // ① 이벤트 스트림 복사 — seqNo/transactionTime 보존 (bi-temporal 유지)
            onPhase(MigrationPhase.COPY_STREAM)
            val stream = sourceCell.readStream(aggregateId)
            if (stream.isNotEmpty()) importer.importStream(stream)

            // ② 대상 셀 프로젝션 오프셋 캐치업
            onPhase(MigrationPhase.CATCH_UP_OFFSETS)
            targetCell.projectors.forEach { projector -> while (projector.catchUpOnce() > 0) Unit }

            // ③ 라우팅 스위치 — 이후 모든 조회/쓰기는 대상 셀로
            onPhase(MigrationPhase.SWITCH_ROUTING)
            fabric.switchRoute(aggregateId, targetCellId)

            // ④ 소스에 tombstone — 데이터는 남기되 접근은 봉인
            onPhase(MigrationPhase.TOMBSTONE_SOURCE)
            sourceCell.tombstone(aggregateId)

            return MigrationReport(aggregateId, sourceCell.cellId, targetCellId, copiedEvents = stream.size)
        } finally {
            fabric.endMigration(aggregateId)
        }
    }
}
