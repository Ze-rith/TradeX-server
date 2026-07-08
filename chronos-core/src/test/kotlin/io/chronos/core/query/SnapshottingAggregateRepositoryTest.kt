package io.chronos.core.query

import io.chronos.core.event.AggregateId
import io.chronos.core.snapshot.InMemorySnapshotStore
import io.chronos.core.snapshot.Snapshot
import io.chronos.core.store.InMemoryEventStore
import io.chronos.core.testing.PriceChanged
import io.chronos.core.testing.Product
import io.chronos.core.testing.ProductAggregate
import io.chronos.core.testing.ProductRegistered
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.junit.jupiter.api.Test

class SnapshottingAggregateRepositoryTest {
    private val store = InMemoryEventStore()
    private val snapshots = InMemorySnapshotStore<Product>()
    private val repo = SnapshottingAggregateRepository(ProductAggregate, store, snapshots, snapshotEvery = 2)
    private val id = AggregateId.new()
    private val t = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `스냅샷 이후 tail만 리플레이해 같은 상태를 얻는다`() {
        store.append("Product", id, 0, listOf(ProductRegistered(id, t, "desk", 100)))
        store.append("Product", id, 1, listOf(PriceChanged(id, t, 200)))
        repo.maybeSnapshot(id, t)
        snapshots.load(id).shouldNotBeNull().seqNo shouldBe 2

        store.append("Product", id, 2, listOf(PriceChanged(id, t, 300)))
        repo.currentState(id).price shouldBe 300
    }

    @Test
    fun `스냅샷 이전을 겨냥한 소급 정정이 있으면 전체 리플레이로 폴백한다`() {
        val original = PriceChanged(id, t, 200)
        store.append("Product", id, 0, listOf(ProductRegistered(id, t, "desk", 100)))
        store.append("Product", id, 1, listOf(original))
        snapshots.save(Snapshot(id, 2, Product("desk", 200), t))

        store.append("Product", id, 2, listOf(PriceChanged(id, t, 150, correctionOf = original.eventId)))
        repo.currentState(id).price shouldBe 150
    }
}
