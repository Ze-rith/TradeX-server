package io.tradex.core.aggregate

import io.tradex.core.event.DomainEvent

interface Aggregate<S, E : DomainEvent> {
    val type: String
    val initial: S
    fun evolve(state: S, event: E): S
}
