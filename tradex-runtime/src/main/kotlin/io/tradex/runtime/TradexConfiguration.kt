package io.tradex.runtime

import io.tradex.cell.Cell
import io.tradex.cell.CellFabric
import io.tradex.cell.CellMigrator
import io.tradex.core.store.InMemoryEventStore
import io.tradex.membrane.EventSchemaRegistry
import io.tradex.membrane.EventSerde
import io.tradex.membrane.Upcaster
import io.tradex.membrane.UpcasterChain
import io.tradex.membrane.store.PostgresEventStore
import io.tradex.membrane.store.SchemaInitializer
import io.tradex.ontology.OntologyGuard
import io.tradex.ontology.OntologyRegistry
import io.tradex.router.SessionTokenCodec
import java.nio.file.Path
import javax.sql.DataSource
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

fun interface EventSchemaContributor {
    fun contribute(registry: EventSchemaRegistry)
}

@Configuration
@EnableConfigurationProperties(TradexProperties::class)
class TradexConfiguration {
    @Bean
    fun eventSchemaRegistry(contributors: List<EventSchemaContributor>): EventSchemaRegistry =
        EventSchemaRegistry().also { registry -> contributors.forEach { it.contribute(registry) } }

    @Bean
    fun upcasterChain(upcasters: List<Upcaster>): UpcasterChain = UpcasterChain(upcasters)

    @Bean
    fun eventSerde(registry: EventSchemaRegistry, chain: UpcasterChain): EventSerde = EventSerde(registry, chain)

    @Bean
    fun sessionTokenCodec(props: TradexProperties): SessionTokenCodec =
        SessionTokenCodec(props.sessionSecret.toByteArray())

    @Bean
    fun ontologyStartupGuard(props: TradexProperties, registry: EventSchemaRegistry): OntologyStartupGuard {
        OntologyGuard.enforce(OntologyRegistry.load(Path.of(props.ontologyDir)), registry)
        return OntologyStartupGuard
    }

    @Bean
    fun cellFabric(
        props: TradexProperties,
        serde: EventSerde,
        dataSource: ObjectProvider<DataSource>,
    ): CellFabric = when (props.storage) {
        TradexProperties.Storage.IN_MEMORY -> CellFabric(props.cellCount, props.virtualNodes)
        TradexProperties.Storage.POSTGRES -> {
            val ds = requireNotNull(dataSource.ifAvailable) { "tradex.storage=POSTGRES에는 DataSource가 필요하다" }
            SchemaInitializer.applyForCells(ds, (0 until props.cellCount).toList(), props.tablePrefix)
            CellFabric(props.cellCount, props.virtualNodes) { cellId ->
                Cell(cellId, PostgresEventStore(ds, serde, cellId, tableName = "${props.tablePrefix}_$cellId"))
            }
        }
    }

    @Bean
    fun cellMigrator(fabric: CellFabric): CellMigrator = CellMigrator(fabric)
}

object OntologyStartupGuard
