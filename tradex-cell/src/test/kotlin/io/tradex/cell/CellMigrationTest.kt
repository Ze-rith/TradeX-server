package io.tradex.cell

import io.tradex.core.event.AggregateId
import io.tradex.core.query.AggregateRepository
import io.tradex.core.testing.MutableClock
import io.tradex.core.testing.PriceChanged
import io.tradex.core.testing.ProductAggregate
import io.tradex.core.testing.ProductRegistered
import io.tradex.core.store.InMemoryEventStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Test

/**
 * L4 인수 테스트: 마이그레이션 = 스트림 리플레이.
 * 상태가 아니라 이벤트가 진실이므로, 스트림만 옮기면 상태 해시가 동일해야 한다.
 */
class CellMigrationTest {
    private val clock = MutableClock(Instant.parse("2026-01-05T09:00:00Z"))
    private val fabric = CellFabric(cellCount = 3) { id -> Cell(id, InMemoryEventStore(clock, cellId = id)) }
    private val migrator = CellMigrator(fabric)
    private val t = Instant.parse("2026-01-01T00:00:00Z")
    private val v2 = Instant.parse("2026-01-05T00:00:00Z")

    private fun stateHash(id: AggregateId): String {
        val state = AggregateRepository(ProductAggregate, fabric.cellFor(id).store).currentState(id)
        return MessageDigest.getInstance("SHA-256").digest(state.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun writeHistory(id: AggregateId): Instant {
        fabric.append("Product", id, 0, listOf(ProductRegistered(id, t, "keyboard", 1_000)))
        clock.advance(Duration.ofHours(1))
        val original = PriceChanged(id, v2, 1_200)
        fabric.append("Product", id, 1, listOf(original))
        val beforeCorrection = clock.instant()
        clock.advance(Duration.ofDays(1))
        fabric.append("Product", id, 2, listOf(PriceChanged(id, v2, 1_150, correctionOf = original.eventId)))
        return beforeCorrection
    }

    @Test
    fun `마이그레이션 전후 aggregate 상태 해시가 동일하다`() {
        val id = AggregateId.new()
        writeHistory(id)
        val sourceCellId = fabric.cellIdFor(id)
        val targetCellId = (sourceCellId + 1) % 3
        val hashBefore = stateHash(id)

        val report = migrator.migrate(id, targetCellId)

        report.copiedEvents shouldBe 3
        fabric.cellIdFor(id) shouldBe targetCellId
        stateHash(id) shouldBe hashBefore
    }

    @Test
    fun `bi-temporal 시맨틱(seqNo·transactionTime)이 이관 후에도 보존된다`() {
        val id = AggregateId.new()
        val beforeCorrection = writeHistory(id)
        val sourceCellId = fabric.cellIdFor(id)
        val recordsBefore = fabric.readStream(id)

        migrator.migrate(id, (sourceCellId + 1) % 3)

        val recordsAfter = fabric.readStream(id)
        recordsAfter.map { it.seqNo } shouldBe recordsBefore.map { it.seqNo }
        recordsAfter.map { it.transactionTime } shouldBe recordsBefore.map { it.transactionTime }
        recordsAfter.map { it.event } shouldBe recordsBefore.map { it.event }

        // asAt(정정 이전) 조회가 이관 후에도 "그 당시 지식"을 복원한다
        val repo = AggregateRepository(ProductAggregate, fabric.cellFor(id).store)
        repo.stateAsAt(id, beforeCorrection).price shouldBe 1_200
        repo.stateAsOf(id, v2).price shouldBe 1_150
    }

    @Test
    fun `소스 셀에는 tombstone이 남아 직접 접근이 봉인된다`() {
        val id = AggregateId.new()
        writeHistory(id)
        val sourceCell = fabric.cellFor(id)

        migrator.migrate(id, (sourceCell.cellId + 1) % 3)

        sourceCell.isTombstoned(id) shouldBe true
        shouldThrow<TombstonedAggregateException> { sourceCell.readStream(id) }
        // 데이터 자체는 물리적으로 보존 (진실은 지워지지 않는다)
        sourceCell.store.readStream(id).size shouldBe 3
        // 패브릭 경유 접근은 대상 셀로 라우팅되므로 정상
        fabric.readStream(id).size shouldBe 3
    }

    @Test
    fun `마이그레이션 진행 중 쓰기는 거절된다 - D10`() {
        val id = AggregateId.new()
        writeHistory(id)
        val targetCellId = (fabric.cellIdFor(id) + 1) % 3

        migrator.migrate(id, targetCellId) { phase ->
            if (phase == MigrationPhase.CATCH_UP_OFFSETS) {
                shouldThrow<MigrationInProgressException> {
                    fabric.append("Product", id, 3, listOf(PriceChanged(id, v2, 9_999)))
                }
            }
        }

        // 완료 후에는 대상 셀에서 쓰기가 다시 허용된다
        fabric.append("Product", id, 3, listOf(PriceChanged(id, v2, 1_100)))
        fabric.readStream(id).size shouldBe 4
    }

    @Test
    fun `같은 셀로의 마이그레이션은 no-op이다`() {
        val id = AggregateId.new()
        writeHistory(id)
        val cellId = fabric.cellIdFor(id)

        migrator.migrate(id, cellId).copiedEvents shouldBe 0
        fabric.readStream(id).size shouldBe 3
    }
}
