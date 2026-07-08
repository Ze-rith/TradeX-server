package io.chronos.cell

import io.chronos.core.event.AggregateId
import io.chronos.core.testing.ProductRegistered
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.junit.jupiter.api.Test

class CellFabricTest {
    private val fabric = CellFabric(cellCount = 3)
    private val t = Instant.parse("2026-01-01T00:00:00Z")

    private fun aggregateOn(cellId: Int): AggregateId {
        var id = AggregateId.new()
        while (fabric.cellIdFor(id) != cellId) id = AggregateId.new()
        return id
    }

    @Test
    fun `blast radius 격리 - 셀 하나가 다운돼도 다른 셀 요청은 정상 처리된다`() {
        val onCell0 = aggregateOn(0)
        val onCell1 = aggregateOn(1)
        fabric.append("Product", onCell0, 0, listOf(ProductRegistered(onCell0, t, "a", 100)))
        fabric.append("Product", onCell1, 0, listOf(ProductRegistered(onCell1, t, "b", 200)))

        fabric.down(0)

        // 죽은 셀의 aggregate → 명시적 실패
        shouldThrow<CellDownException> { fabric.readStream(onCell0) }
        shouldThrow<CellDownException> {
            fabric.append("Product", onCell0, 1, listOf(ProductRegistered(onCell0, t, "a2", 100)))
        }

        // 다른 셀은 읽기/쓰기 모두 무사
        fabric.readStream(onCell1).size shouldBe 1
        fabric.append("Product", onCell1, 1, listOf(ProductRegistered(onCell1, t, "b2", 300)))
        fabric.readStream(onCell1).size shouldBe 2

        fabric.up(0)
        fabric.readStream(onCell0).size shouldBe 1
    }

    @Test
    fun `이벤트는 자기 셀 파티션에만 기록된다`() {
        val onCell2 = aggregateOn(2)
        fabric.append("Product", onCell2, 0, listOf(ProductRegistered(onCell2, t, "c", 100)))

        fabric.cells.getValue(2).store.lastGlobalSeq() shouldBe 1
        fabric.cells.getValue(0).store.lastGlobalSeq() shouldBe 0
        fabric.cells.getValue(1).store.lastGlobalSeq() shouldBe 0
    }
}
