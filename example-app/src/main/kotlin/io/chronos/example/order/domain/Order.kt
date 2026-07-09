package io.chronos.example.order.domain

import io.chronos.core.aggregate.Aggregate
import io.chronos.example.order.contract.OrderCancelled
import io.chronos.example.order.contract.OrderConfirmed
import io.chronos.example.order.contract.OrderEvent
import io.chronos.example.order.contract.OrderPlaced

enum class OrderStatus { PLACED, CONFIRMED, CANCELLED }

data class Order(
    val status: OrderStatus? = null,
    val productName: String? = null,
    val amount: Long? = null,
    val currency: String? = null,
    val cancelReason: String? = null,
)

object OrderAggregate : Aggregate<Order, OrderEvent> {
    override val type = "Order"
    override val initial = Order()

    override fun evolve(state: Order, event: OrderEvent): Order = when (event) {
        is OrderPlaced -> state.copy(
            status = OrderStatus.PLACED,
            productName = event.productName,
            amount = event.amount,
            currency = event.currency,
        )
        is OrderConfirmed -> state.copy(status = OrderStatus.CONFIRMED)
        is OrderCancelled -> state.copy(status = OrderStatus.CANCELLED, cancelReason = event.reason)
    }
}
