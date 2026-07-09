package io.tradex.core.event

import java.time.Instant

interface DomainEvent {
    val eventId: EventId
    val aggregateId: AggregateId
    val validTime: Instant
    val correctionOf: EventId?
        get() = null
}
