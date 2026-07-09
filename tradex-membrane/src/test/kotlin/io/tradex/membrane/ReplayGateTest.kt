package io.tradex.membrane

import io.tradex.core.event.AggregateId
import io.tradex.core.event.DomainEvent
import io.tradex.core.event.EventId
import io.tradex.membrane.testkit.ReplayGate
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Instant
import org.junit.jupiter.api.Test

class ReplayGateTest {
    @Test
    fun `등록된 모든 (type, version) fixture가 로드-업캐스트-역직렬화-evolve를 통과한다`() {
        var state = OrderTestAggregate.initial
        val gate = ReplayGate(orderSchemaRegistry(), orderSerde(), applyEvolve = { event ->
            state = OrderTestAggregate.evolve(state, event as OrderPlaced)
        })

        gate.verify()
        state.amount shouldBe 15_000 + 22_000 + 9_900
    }

    @Test
    fun `@EventSchema가 있는데 fixture가 없으면 실패 목록에 오른다`() {
        val registry = orderSchemaRegistry().apply {
            register("StockReserved", 2, StockReservedWithoutFixture::class)
        }
        val gate = ReplayGate(registry, EventSerde(registry, UpcasterChain(listOf(OrderPlacedV1ToV2(), OrderPlacedV2ToV3()))), applyEvolve = {})

        val failures = gate.failures()
        failures.size shouldBe 2
        failures.forEach { it shouldContain "fixture 누락" }
        failures.first() shouldContain "StockReserved"
    }
}

private data class StockReservedWithoutFixture(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : DomainEvent
