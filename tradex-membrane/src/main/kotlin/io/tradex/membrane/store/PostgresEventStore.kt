package io.tradex.membrane.store

import io.tradex.core.event.AggregateId
import io.tradex.core.event.DomainEvent
import io.tradex.core.event.EventId
import io.tradex.core.event.EventRecord
import io.tradex.core.store.EventStore
import io.tradex.core.store.InvalidCorrectionException
import io.tradex.core.store.OptimisticConcurrencyException
import io.tradex.core.store.StreamImporter
import io.tradex.membrane.EventSerde
import java.sql.ResultSet
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.sql.DataSource
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class PostgresEventStore(
    dataSource: DataSource,
    private val serde: EventSerde,
    private val cellId: Int = 0,
    private val clock: Clock = Clock.systemUTC(),
    private val tableName: String = "event_store",
) : EventStore, StreamImporter {
    init {
        require(tableName.matches(Regex("[a-z_][a-z0-9_]*"))) { "invalid table name: $tableName" }
    }

    private val jdbc = JdbcClient.create(dataSource)
    private val tx = TransactionTemplate(DataSourceTransactionManager(dataSource))

    override fun append(
        aggregateType: String,
        aggregateId: AggregateId,
        expectedSeqNo: Long,
        events: List<DomainEvent>,
    ): List<EventRecord<DomainEvent>> {
        require(events.isNotEmpty()) { "events must not be empty" }
        require(events.all { it.aggregateId == aggregateId }) { "all events must belong to $aggregateId" }

        try {
            return tx.execute { doAppend(aggregateType, aggregateId, expectedSeqNo, events) }!!
        } catch (e: DuplicateKeyException) {
            throw OptimisticConcurrencyException(aggregateId, expectedSeqNo, currentSeqNo(aggregateId))
        }
    }

    private fun doAppend(
        aggregateType: String,
        aggregateId: AggregateId,
        expectedSeqNo: Long,
        events: List<DomainEvent>,
    ): List<EventRecord<DomainEvent>> {
        val current = currentSeqNo(aggregateId)
        if (current != expectedSeqNo) throw OptimisticConcurrencyException(aggregateId, expectedSeqNo, current)

        val insertedInBatch = HashMap<EventId, Pair<Long, String>>()
        val transactionTime = clock.instant()

        return events.mapIndexed { index, event ->
            val serialized = serde.serialize(event)
            val correctionGlobalSeq = event.correctionOf?.let { targetId ->
                resolveCorrectionTarget(aggregateId, targetId, serialized.type, insertedInBatch)
            }
            val seqNo = expectedSeqNo + index + 1
            val globalSeq = jdbc.sql(
                """
                INSERT INTO $tableName
                    (cell_id, aggregate_type, aggregate_id, seq_no, event_id, event_type, event_version,
                     payload, valid_time, transaction_time, correction_of)
                VALUES
                    (:cellId, :aggregateType, :aggregateId, :seqNo, :eventId, :eventType, :eventVersion,
                     :payload::jsonb, :validTime, :transactionTime, :correctionOf)
                RETURNING global_seq
                """.trimIndent(),
            )
                .param("cellId", cellId)
                .param("aggregateType", aggregateType)
                .param("aggregateId", aggregateId.value)
                .param("seqNo", seqNo)
                .param("eventId", event.eventId.value)
                .param("eventType", serialized.type)
                .param("eventVersion", serialized.version)
                .param("payload", serialized.json)
                .param("validTime", event.validTime.atOffset())
                .param("transactionTime", transactionTime.atOffset())
                .param("correctionOf", correctionGlobalSeq)
                .query(Long::class.java)
                .single()

            insertedInBatch[event.eventId] = globalSeq to serialized.type
            EventRecord(globalSeq, cellId, aggregateType, seqNo, transactionTime, event)
        }
    }

    private fun resolveCorrectionTarget(
        aggregateId: AggregateId,
        targetId: EventId,
        correctionType: String,
        insertedInBatch: Map<EventId, Pair<Long, String>>,
    ): Long {
        val (globalSeq, targetType) = insertedInBatch[targetId]
            ?: jdbc.sql("SELECT global_seq, event_type FROM $tableName WHERE event_id = :eventId AND aggregate_id = :aggregateId")
                .param("eventId", targetId.value)
                .param("aggregateId", aggregateId.value)
                .query { rs, _ -> rs.getLong("global_seq") to rs.getString("event_type") }
                .optional()
                .orElseThrow { InvalidCorrectionException("correction target $targetId not found in stream $aggregateId") }

        if (targetType != correctionType) {
            throw InvalidCorrectionException("correction type $correctionType != original type $targetType ($targetId)")
        }
        return globalSeq
    }

    override fun importStream(records: List<EventRecord<DomainEvent>>) {
        require(records.isNotEmpty()) { "records must not be empty" }
        val aggregateId = records.first().aggregateId
        require(records.all { it.aggregateId == aggregateId }) { "single-stream import only" }

        tx.execute {
            check(currentSeqNo(aggregateId) == 0L) { "target already has stream for $aggregateId" }
            val idToGlobalSeq = HashMap<EventId, Long>()
            for (record in records.sortedBy { it.seqNo }) {
                val serialized = serde.serialize(record.event)
                val correctionGlobalSeq = record.event.correctionOf?.let { idToGlobalSeq.getValue(it) }
                val globalSeq = jdbc.sql(
                    """
                    INSERT INTO $tableName
                        (cell_id, aggregate_type, aggregate_id, seq_no, event_id, event_type, event_version,
                         payload, valid_time, transaction_time, correction_of)
                    VALUES
                        (:cellId, :aggregateType, :aggregateId, :seqNo, :eventId, :eventType, :eventVersion,
                         :payload::jsonb, :validTime, :transactionTime, :correctionOf)
                    RETURNING global_seq
                    """.trimIndent(),
                )
                    .param("cellId", cellId)
                    .param("aggregateType", record.aggregateType)
                    .param("aggregateId", aggregateId.value)
                    .param("seqNo", record.seqNo)
                    .param("eventId", record.eventId.value)
                    .param("eventType", serialized.type)
                    .param("eventVersion", serialized.version)
                    .param("payload", serialized.json)
                    .param("validTime", record.event.validTime.atOffset())
                    .param("transactionTime", record.transactionTime.atOffset())
                    .param("correctionOf", correctionGlobalSeq)
                    .query(Long::class.java)
                    .single()
                idToGlobalSeq[record.eventId] = globalSeq
            }
        }
    }

    override fun readStream(aggregateId: AggregateId, afterSeqNo: Long): List<EventRecord<DomainEvent>> =
        jdbc.sql("SELECT * FROM $tableName WHERE aggregate_id = :aggregateId AND seq_no > :afterSeqNo ORDER BY seq_no")
            .param("aggregateId", aggregateId.value)
            .param("afterSeqNo", afterSeqNo)
            .query { rs, _ -> toRecord(rs) }
            .list()

    override fun readStreamAsAt(aggregateId: AggregateId, transactionTime: Instant): List<EventRecord<DomainEvent>> =
        jdbc.sql(
            "SELECT * FROM $tableName WHERE aggregate_id = :aggregateId AND transaction_time <= :asAt ORDER BY seq_no",
        )
            .param("aggregateId", aggregateId.value)
            .param("asAt", transactionTime.atOffset())
            .query { rs, _ -> toRecord(rs) }
            .list()

    override fun readAll(afterGlobalSeq: Long, limit: Int): List<EventRecord<DomainEvent>> =
        jdbc.sql("SELECT * FROM $tableName WHERE global_seq > :after ORDER BY global_seq LIMIT :limit")
            .param("after", afterGlobalSeq)
            .param("limit", limit)
            .query { rs, _ -> toRecord(rs) }
            .list()

    override fun lastGlobalSeq(): Long =
        jdbc.sql("SELECT coalesce(max(global_seq), 0) FROM $tableName")
            .query(Long::class.java)
            .single()

    private fun currentSeqNo(aggregateId: AggregateId): Long =
        jdbc.sql("SELECT coalesce(max(seq_no), 0) FROM $tableName WHERE aggregate_id = :aggregateId")
            .param("aggregateId", aggregateId.value)
            .query(Long::class.java)
            .single()

    private fun toRecord(rs: ResultSet): EventRecord<DomainEvent> {
        val event = serde.deserialize(
            type = rs.getString("event_type"),
            storedVersion = rs.getInt("event_version"),
            payloadJson = rs.getString("payload"),
        )
        return EventRecord(
            globalSeq = rs.getLong("global_seq"),
            cellId = rs.getInt("cell_id"),
            aggregateType = rs.getString("aggregate_type"),
            seqNo = rs.getLong("seq_no"),
            transactionTime = rs.getObject("transaction_time", OffsetDateTime::class.java).toInstant(),
            event = event,
        )
    }
}

private fun Instant.atOffset(): OffsetDateTime = atOffset(ZoneOffset.UTC)
