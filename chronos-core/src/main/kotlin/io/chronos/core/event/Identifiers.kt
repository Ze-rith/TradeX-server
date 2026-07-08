package io.chronos.core.event

import java.util.UUID

@JvmInline
value class EventId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun new(): EventId = EventId(UuidV7.generate())
        fun of(value: String): EventId = EventId(UUID.fromString(value))
    }
}

@JvmInline
value class AggregateId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun new(): AggregateId = AggregateId(UuidV7.generate())
        fun of(value: String): AggregateId = AggregateId(UUID.fromString(value))
    }
}
