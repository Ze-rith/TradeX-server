package io.tradex.router

import io.tradex.core.event.DomainEvent
import io.tradex.core.event.EventRecord

interface Projection {
    val name: String
    fun apply(record: EventRecord<DomainEvent>)
}

interface ProjectionOffsetStore {

    fun lastProcessed(projectionName: String): Long
    fun update(projectionName: String, cellId: Int, globalSeq: Long)
}

class InMemoryProjectionOffsetStore : ProjectionOffsetStore {
    private val offsets = java.util.concurrent.ConcurrentHashMap<String, Long>()

    override fun lastProcessed(projectionName: String): Long = offsets[projectionName] ?: 0L

    override fun update(projectionName: String, cellId: Int, globalSeq: Long) {
        offsets[projectionName] = globalSeq
    }
}
