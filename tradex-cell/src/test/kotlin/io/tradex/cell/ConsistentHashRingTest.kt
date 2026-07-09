package io.tradex.cell

import io.tradex.core.event.AggregateId
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ConsistentHashRingTest {
    private val ring = ConsistentHashRing(cellIds = listOf(0, 1, 2), virtualNodes = 128)

    @Test
    fun `같은 aggregateId는 항상 같은 셀로 라우팅된다`() {
        val id = AggregateId.new()
        val first = ring.route(id)
        repeat(100) { ring.route(id) shouldBe first }
    }

    @Test
    fun `가상 노드 덕에 부하가 셀들에 대체로 고르게 퍼진다`() {
        val counts = (1..3_000).map { ring.route(AggregateId.new()) }
            .groupingBy { it }.eachCount()

        counts.keys shouldBe setOf(0, 1, 2)

        counts.values.forEach { it shouldBeGreaterThan 500 }
    }

    @Test
    fun `셀 제거 시 남은 셀들의 기존 키 대부분은 그대로다`() {
        val ids = (1..1_000).map { AggregateId.new() }
        val before = ids.associateWith { ring.route(it) }

        val shrunk = ConsistentHashRing(cellIds = listOf(0, 1), virtualNodes = 128)
        val moved = ids.count { id -> before.getValue(id) != 2 && shrunk.route(id) != before.getValue(id) }

        val stayedPopulation = ids.count { before.getValue(it) != 2 }
        (moved * 100 / stayedPopulation) shouldBe 0
    }
}
