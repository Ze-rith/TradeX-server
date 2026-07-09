package io.tradex.membrane

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import io.tradex.core.aggregate.Aggregate
import io.tradex.core.event.AggregateId
import io.tradex.core.event.DomainEvent
import io.tradex.core.event.EventId
import java.time.Instant

@EventSchema(type = "OrderPlaced", version = 3)
data class OrderPlaced(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val amount: Long,
    val currency: String = "KRW",
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : DomainEvent

class OrderPlacedV1ToV2 : Upcaster {
    override val type = "OrderPlaced"
    override val fromVersion = 1
    override fun upcast(old: JsonNode): JsonNode = (old.deepCopy() as ObjectNode).apply {
        set<JsonNode>("amount", remove("price"))
    }
}

class OrderPlacedV2ToV3 : Upcaster {
    override val type = "OrderPlaced"
    override val fromVersion = 2
    override fun upcast(old: JsonNode): JsonNode = (old.deepCopy() as ObjectNode).apply {
        if (!has("currency")) put("currency", "KRW")
    }
}

data class OrderTotals(val amount: Long = 0, val currency: String? = null)

object OrderTestAggregate : Aggregate<OrderTotals, OrderPlaced> {
    override val type = "Order"
    override val initial = OrderTotals()
    override fun evolve(state: OrderTotals, event: OrderPlaced) =
        OrderTotals(state.amount + event.amount, event.currency)
}

fun orderSchemaRegistry() = EventSchemaRegistry().apply { register(OrderPlaced::class) }

fun orderSerde(upcasters: List<Upcaster> = listOf(OrderPlacedV1ToV2(), OrderPlacedV2ToV3())) =
    EventSerde(orderSchemaRegistry(), UpcasterChain(upcasters))
