package io.tradex.runtime.store

import io.tradex.router.ProjectionOffsetStore
import javax.sql.DataSource
import org.springframework.jdbc.core.simple.JdbcClient

/** projection_offset 테이블 구현. 인메모리 프로젝션과 함께 쓸 때는 기동 시 [reset]으로 재구축한다. */
class PostgresProjectionOffsetStore(dataSource: DataSource) : ProjectionOffsetStore {
    private val jdbc = JdbcClient.create(dataSource)

    override fun lastProcessed(projectionName: String): Long =
        jdbc.sql("SELECT last_global_seq FROM projection_offset WHERE projection_name = :name")
            .param("name", projectionName)
            .query(Long::class.java)
            .optional()
            .orElse(0L)

    override fun update(projectionName: String, cellId: Int, globalSeq: Long) {
        jdbc.sql(
            """
            INSERT INTO projection_offset (projection_name, cell_id, last_global_seq)
            VALUES (:name, :cellId, :seq)
            ON CONFLICT (projection_name) DO UPDATE SET last_global_seq = excluded.last_global_seq
            """.trimIndent(),
        )
            .param("name", projectionName)
            .param("cellId", cellId)
            .param("seq", globalSeq)
            .update()
    }

    fun reset(projectionName: String) {
        jdbc.sql("DELETE FROM projection_offset WHERE projection_name = :name").param("name", projectionName).update()
    }
}
