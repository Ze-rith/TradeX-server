package io.chronos.example.order.contract

import io.chronos.core.event.AggregateId
import io.chronos.core.event.DomainEvent
import io.chronos.core.event.EventId
import io.chronos.membrane.EventSchema
import java.time.Instant

/**
 * 주문 Bounded Context가 발행하는 이벤트 계약.
 * 도메인 패키지가 아니라 여기(contract)에 있는 이유: @EventSchema는 membrane(경계막)의
 * 관심사이고, 도메인 패키지는 chronos-core 추상화만 알아야 하기 때문 (Konsist 강제).
 */
sealed interface OrderEvent : DomainEvent

@EventSchema(type = "OrderPlaced", version = 1)
data class OrderPlaced(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val productName: String,
    val amount: Long,
    val currency: String = "KRW",
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : OrderEvent

@EventSchema(type = "OrderConfirmed", version = 1)
data class OrderConfirmed(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : OrderEvent

@EventSchema(type = "OrderCancelled", version = 1)
data class OrderCancelled(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val reason: String,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : OrderEvent
