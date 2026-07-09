package io.tradex.core.store

import io.tradex.core.event.AggregateId
import io.tradex.core.event.DomainEvent
import io.tradex.core.event.EventRecord
import java.time.Instant

interface EventStore {
    fun append(
        aggregateType: String,
        aggregateId: AggregateId,
        expectedSeqNo: Long,
        events: List<DomainEvent>,
    ): List<EventRecord<DomainEvent>>

    fun readStream(aggregateId: AggregateId, afterSeqNo: Long = 0L): List<EventRecord<DomainEvent>>

    fun readStreamAsAt(aggregateId: AggregateId, transactionTime: Instant): List<EventRecord<DomainEvent>>

    fun readAll(afterGlobalSeq: Long = 0L, limit: Int = 1_000): List<EventRecord<DomainEvent>>

    fun lastGlobalSeq(): Long
}

interface StreamImporter {
    fun importStream(records: List<EventRecord<DomainEvent>>)
}

class OptimisticConcurrencyException(
    aggregateId: AggregateId,
    expected: Long,
    actual: Long,
) : RuntimeException("aggregate=$aggregateId expectedSeqNo=$expected actualSeqNo=$actual")

class InvalidCorrectionException(message: String) : RuntimeException(message)
