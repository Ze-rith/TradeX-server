package io.tradex.core.testing

import io.tradex.core.aggregate.Aggregate
import io.tradex.core.event.AggregateId
import io.tradex.core.event.DomainEvent
import io.tradex.core.event.EventId
import java.time.Instant

/** bi-temporal 시나리오 검증용 미니 도메인. */
data class Product(val name: String? = null, val price: Long? = null)

sealed interface ProductEvent : DomainEvent

data class ProductRegistered(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val name: String,
    val price: Long,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : ProductEvent

data class PriceChanged(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val price: Long,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : ProductEvent

object ProductAggregate : Aggregate<Product, ProductEvent> {
    override val type = "Product"
    override val initial = Product()

    override fun evolve(state: Product, event: ProductEvent): Product = when (event) {
        is ProductRegistered -> Product(name = event.name, price = event.price)
        is PriceChanged -> state.copy(price = event.price)
    }
}
