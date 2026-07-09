package io.tradex.membrane.store

import javax.sql.DataSource

object SchemaInitializer {
    fun apply(dataSource: DataSource) {
        execute(dataSource, bundledDdl())
    }

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
