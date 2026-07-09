package io.tradex.membrane.store

import javax.sql.DataSource

/** 번들된 schema.sql을 적용한다 (idempotent). 테스트/로컬 기동용. */
object SchemaInitializer {
    fun apply(dataSource: DataSource) {
        execute(dataSource, bundledDdl())
    }

    /**
     * 셀 파티션용: event_store를 셀별 테이블(event_store_0, ...)로 복제 생성한다.
     * snapshot/projection_offset은 공유 테이블 그대로.
     */
    fun applyForCells(dataSource: DataSource, cellIds: Collection<Int>, tableBaseName: String = "event_store") {
        val ddl = bundledDdl()
        execute(dataSource, ddl)
        for (cellId in cellIds) {
            execute(dataSource, ddl.replace(Regex("\\bevent_store\\b"), "${tableBaseName}_$cellId"))
        }
    }

    private fun bundledDdl(): String =
        requireNotNull(javaClass.getResourceAsStream("/io/tradex/membrane/schema.sql")) {
            "schema.sql not found on classpath"
        }.bufferedReader().readText()

    private fun execute(dataSource: DataSource, ddl: String) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                ddl.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach(statement::execute)
            }
        }
    }
}
