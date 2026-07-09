package io.tradex.core.event

import java.time.Instant

data class EventRecord<out E : DomainEvent>(
    val globalSeq: Long,
    val cellId: Int,
    val aggregateType: String,
    val seqNo: Long,
    val transactionTime: Instant,
    val event: E,
) {
    val eventId: EventId get() = event.eventId
    val aggregateId: AggregateId get() = event.aggregateId
}
