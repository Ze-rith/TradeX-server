package io.chronos.runtime

import io.chronos.cell.Cell
import io.chronos.cell.CellFabric
import io.chronos.cell.CellMigrator
import io.chronos.core.store.InMemoryEventStore
import io.chronos.membrane.EventSchemaRegistry
import io.chronos.membrane.EventSerde
import io.chronos.membrane.Upcaster
import io.chronos.membrane.UpcasterChain
import io.chronos.membrane.store.PostgresEventStore
import io.chronos.membrane.store.SchemaInitializer
import io.chronos.ontology.OntologyGuard
import io.chronos.ontology.OntologyRegistry
import io.chronos.router.SessionTokenCodec
import java.nio.file.Path
import javax.sql.DataSource
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** 애플리케이션이 자기 이벤트 클래스를 레지스트리에 등록하는 진입점. */
fun interface EventSchemaContributor {
    fun contribute(registry: EventSchemaRegistry)
}

@Configuration
@EnableConfigurationProperties(ChronosProperties::class)
class ChronosConfiguration {
    @Bean
    fun eventSchemaRegistry(contributors: List<EventSchemaContributor>): EventSchemaRegistry =
        EventSchemaRegistry().also { registry -> contributors.forEach { it.contribute(registry) } }

    @Bean
    fun upcasterChain(upcasters: List<Upcaster>): UpcasterChain = UpcasterChain(upcasters)

    @Bean
    fun eventSerde(registry: EventSchemaRegistry, chain: UpcasterChain): EventSerde = EventSerde(registry, chain)

    @Bean
    fun sessionTokenCodec(props: ChronosProperties): SessionTokenCodec =
        SessionTokenCodec(props.sessionSecret.toByteArray())

    /**
     * L5 가드: Bounded Context를 넘는 이벤트는 APPROVED 스키마만 —
     * 위반 시 컨텍스트 초기화 단계에서 기동 실패 (fail-fast).
     */
    @Bean
    fun ontologyStartupGuard(props: ChronosProperties, registry: EventSchemaRegistry): OntologyStartupGuard {
        OntologyGuard.enforce(OntologyRegistry.load(Path.of(props.ontologyDir)), registry)
        return OntologyStartupGuard
    }

    @Bean
    fun cellFabric(
        props: ChronosProperties,
        serde: EventSerde,
        dataSource: ObjectProvider<DataSource>,
    ): CellFabric = when (props.storage) {
        ChronosProperties.Storage.IN_MEMORY -> CellFabric(props.cellCount, props.virtualNodes)
        ChronosProperties.Storage.POSTGRES -> {
            val ds = requireNotNull(dataSource.ifAvailable) { "chronos.storage=POSTGRES에는 DataSource가 필요하다" }
            SchemaInitializer.applyForCells(ds, (0 until props.cellCount).toList())
            CellFabric(props.cellCount, props.virtualNodes) { cellId ->
                Cell(cellId, PostgresEventStore(ds, serde, cellId, tableName = "event_store_$cellId"))
            }
        }
    }

    @Bean
    fun cellMigrator(fabric: CellFabric): CellMigrator = CellMigrator(fabric)
}

/** ontologyStartupGuard 빈의 마커 (검증은 빈 생성 시점에 이미 수행됨). */
object OntologyStartupGuard
