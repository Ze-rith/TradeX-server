package io.chronos.membrane.store

import com.fasterxml.jackson.databind.ObjectMapper
import io.chronos.core.event.AggregateId
import io.chronos.core.snapshot.Snapshot
import io.chronos.core.snapshot.SnapshotStore
import io.chronos.membrane.ChronosJson
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.sql.DataSource
import org.springframework.jdbc.core.simple.JdbcClient

class PostgresSnapshotStore<S : Any>(
    dataSource: DataSource,
    private val stateClass: Class<S>,
    private val objectMapper: ObjectMapper = ChronosJson.mapper(),
) : SnapshotStore<S> {
    private val jdbc = JdbcClient.create(dataSource)

    override fun save(snapshot: Snapshot<S>) {
        jdbc.sql(
            """
            INSERT INTO aggregate_snapshot (aggregate_id, seq_no, state, taken_at)
            VALUES (:aggregateId, :seqNo, :state::jsonb, :takenAt)
            ON CONFLICT (aggregate_id)
                DO UPDATE SET seq_no = excluded.seq_no, state = excluded.state, taken_at = excluded.taken_at
            """.trimIndent(),
        )
            .param("aggregateId", snapshot.aggregateId.value)
            .param("seqNo", snapshot.seqNo)
            .param("state", objectMapper.writeValueAsString(snapshot.state))
            .param("takenAt", snapshot.takenAt.atOffset(ZoneOffset.UTC))
            .update()
    }

    override fun load(aggregateId: AggregateId): Snapshot<S>? =
        jdbc.sql("SELECT seq_no, state, taken_at FROM aggregate_snapshot WHERE aggregate_id = :aggregateId")
            .param("aggregateId", aggregateId.value)
            .query { rs, _ ->
                Snapshot(
                    aggregateId = aggregateId,
                    seqNo = rs.getLong("seq_no"),
                    state = objectMapper.readValue(rs.getString("state"), stateClass),
                    takenAt = rs.getObject("taken_at", OffsetDateTime::class.java).toInstant(),
                )
            }
            .optional()
            .orElse(null)
}
