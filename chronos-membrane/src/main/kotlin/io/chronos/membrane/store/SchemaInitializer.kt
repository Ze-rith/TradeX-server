package io.chronos.membrane.store

import javax.sql.DataSource

/** 번들된 schema.sql을 적용한다 (idempotent). 테스트/로컬 기동용. */
object SchemaInitializer {
    fun apply(dataSource: DataSource) {
        val ddl = requireNotNull(javaClass.getResourceAsStream("/io/chronos/membrane/schema.sql")) {
            "schema.sql not found on classpath"
        }.bufferedReader().readText()

        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                ddl.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach(statement::execute)
            }
        }
    }
}
