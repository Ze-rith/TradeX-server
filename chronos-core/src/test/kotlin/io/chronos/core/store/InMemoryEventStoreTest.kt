package io.chronos.core.store

import io.chronos.core.event.AggregateId
import io.chronos.core.testing.PriceChanged
import io.chronos.core.testing.ProductRegistered
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.junit.jupiter.api.Test

class InMemoryEventStoreTest {
    private val store = InMemoryEventStore()
    private val id = AggregateId.new()
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `append는 seqNo와 globalSeq를 순서대로 부여한다`() {
        store.append("Product", id, 0, listOf(ProductRegistered(id, now, "a", 100)))
        store.append("Product", id, 1, listOf(PriceChanged(id, now, 200), PriceChanged(id, now, 300)))

        val records = store.readStream(id)
        records.map { it.seqNo } shouldContainInOrder listOf(1L, 2L, 3L)
        records.map { it.globalSeq } shouldContainInOrder listOf(1L, 2L, 3L)
        store.lastGlobalSeq() shouldBe 3L
    }

    @Test
    fun `expectedSeqNo가 어긋나면 낙관적 동시성 예외`() {
        store.append("Product", id, 0, listOf(ProductRegistered(id, now, "a", 100)))

        shouldThrow<OptimisticConcurrencyException> {
            store.append("Product", id, 0, listOf(PriceChanged(id, now, 200)))
        }
        shouldThrow<OptimisticConcurrencyException> {
            store.append("Product", id, 5, listOf(PriceChanged(id, now, 200)))
        }
    }

    @Test
    fun `readAll은 afterGlobalSeq 이후만 순서대로 반환한다`() {
        val other = AggregateId.new()
        store.append("Product", id, 0, listOf(ProductRegistered(id, now, "a", 100)))
        store.append("Product", other, 0, listOf(ProductRegistered(other, now, "b", 200)))

        val tail = store.readAll(afterGlobalSeq = 1)
        tail.size shouldBe 1
        tail.single().aggregateId shouldBe other
    }
}
