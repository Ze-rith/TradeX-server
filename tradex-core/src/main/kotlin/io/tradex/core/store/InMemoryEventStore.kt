package io.tradex.core.store

import io.tradex.core.event.AggregateId
import io.tradex.core.event.DomainEvent
import io.tradex.core.event.EventId
import io.tradex.core.event.EventRecord
import java.time.Clock
import java.time.Instant

/**
 * 단일 JVM 인메모리 이벤트 스토어. [clock]은 transactionTime 부여용 (테스트에서 주입).
 */
class InMemoryEventStore(
    private val clock: Clock = Clock.systemUTC(),
    private val cellId: Int = 0,
) : EventStore, StreamImporter {
    private val lock = Any()
    private val all = ArrayList<EventRecord<DomainEvent>>()
    private val streams = HashMap<AggregateId, MutableList<EventRecord<DomainEvent>>>()

    override fun append(
        aggregateType: String,
        aggregateId: AggregateId,
        expectedSeqNo: Long,
        events: List<DomainEvent>,
    ): List<EventRecord<DomainEvent>> = synchronized(lock) {
        require(events.isNotEmpty()) { "events must not be empty" }
        require(events.all { it.aggregateId == aggregateId }) { "all events must belong to $aggregateId" }

        val stream = streams.getOrPut(aggregateId) { mutableListOf() }
        val currentSeqNo = stream.lastOrNull()?.seqNo ?: 0L
        if (currentSeqNo != expectedSeqNo) {
            throw OptimisticConcurrencyException(aggregateId, expectedSeqNo, currentSeqNo)
        }

        val known = HashMap<EventId, DomainEvent>()
        stream.forEach { known[it.eventId] = it.event }

        val transactionTime = clock.instant()
        var seqNo = currentSeqNo
        val appended = events.map { event ->
            validateCorrection(event, known)
            known[event.eventId] = event
            val record = EventRecord(
                globalSeq = all.size + 1L,
                cellId = cellId,
                aggregateType = aggregateType,
                seqNo = ++seqNo,
                transactionTime = transactionTime,
                event = event,
            )
            all.add(record)
            stream.add(record)
            record
        }
        appended
    }

    private fun validateCorrection(event: DomainEvent, known: Map<EventId, DomainEvent>) {
        val targetId = event.correctionOf ?: return
        val target = known[targetId]
            ?: throw InvalidCorrectionException("correction target $targetId not found in stream ${event.aggregateId}")
        if (target::class != event::class) {
            throw InvalidCorrectionException(
                "correction ${event.eventId} type ${event::class.simpleName} != original type ${target::class.simpleName}",
            )
        }
    }

    override fun importStream(records: List<EventRecord<DomainEvent>>) = synchronized(lock) {
        require(records.isNotEmpty()) { "records must not be empty" }
        val aggregateId = records.first().aggregateId
        require(records.all { it.aggregateId == aggregateId }) { "single-stream import only" }
        require(streams[aggregateId].isNullOrEmpty()) { "target already has stream for $aggregateId" }

        val stream = streams.getOrPut(aggregateId) { mutableListOf() }
        for (record in records.sortedBy { it.seqNo }) {
            // seqNo·transactionTime은 원본 보존, globalSeq·cellId만 이 파티션 기준으로 재부여
            val imported = record.copy(globalSeq = all.size + 1L, cellId = cellId)
            all.add(imported)
            stream.add(imported)
        }
    }

    override fun readStream(aggregateId: AggregateId, afterSeqNo: Long): List<EventRecord<DomainEvent>> =
        synchronized(lock) { streams[aggregateId]?.filter { it.seqNo > afterSeqNo } ?: emptyList() }

    override fun readStreamAsAt(aggregateId: AggregateId, transactionTime: Instant): List<EventRecord<DomainEvent>> =
        synchronized(lock) {
            streams[aggregateId]?.filter { !it.transactionTime.isAfter(transactionTime) } ?: emptyList()
        }

    override fun readAll(afterGlobalSeq: Long, limit: Int): List<EventRecord<DomainEvent>> =
        synchronized(lock) { all.asSequence().filter { it.globalSeq > afterGlobalSeq }.take(limit).toList() }

    override fun lastGlobalSeq(): Long = synchronized(lock) { all.size.toLong() }
}
