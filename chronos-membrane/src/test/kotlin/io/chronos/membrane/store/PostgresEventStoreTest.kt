package io.chronos.membrane.store

import io.chronos.core.event.AggregateId
import io.chronos.core.query.AggregateRepository
import io.chronos.core.query.SnapshottingAggregateRepository
import io.chronos.core.snapshot.Snapshot
import io.chronos.core.store.OptimisticConcurrencyException
import io.chronos.core.testing.MutableClock
import io.chronos.core.testing.PriceChanged
import io.chronos.core.testing.Product
import io.chronos.core.testing.ProductAggregate
import io.chronos.core.testing.ProductRegistered
import io.chronos.membrane.EventSchemaRegistry
import io.chronos.membrane.EventSerde
import io.chronos.membrane.OrderPlaced
import io.chronos.membrane.OrderPlacedV1ToV2
import io.chronos.membrane.OrderPlacedV2ToV3
import io.chronos.membrane.UpcasterChain
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.jdbc.core.simple.JdbcClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class PostgresEventStoreTest {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        lateinit var dataSource: DataSource

        @BeforeAll
        @JvmStatic
        fun initSchema() {
            dataSource = PGSimpleDataSource().apply {
                setUrl(postgres.jdbcUrl)
                user = postgres.username
                password = postgres.password
            }
            SchemaInitializer.apply(dataSource)
        }
    }

    private val registry = EventSchemaRegistry().apply {
        register(OrderPlaced::class)
        register("ProductRegistered", 1, ProductRegistered::class)
        register("PriceChanged", 1, PriceChanged::class)
    }
    private val serde = EventSerde(registry, UpcasterChain(listOf(OrderPlacedV1ToV2(), OrderPlacedV2ToV3())))
    private val clock = MutableClock(Instant.parse("2026-01-05T09:00:00Z"))
    private val store = PostgresEventStore(dataSource, serde, cellId = 0, clock = clock)
    private val repo = AggregateRepository(ProductAggregate, store)

    private val v1 = Instant.parse("2026-01-01T00:00:00Z")
    private val v2 = Instant.parse("2026-01-05T00:00:00Z")

    @Test
    fun `bi-temporal 시나리오가 Postgres에서도 성립한다`() {
        val id = AggregateId.new()
        store.append("Product", id, 0, listOf(ProductRegistered(id, v1, "keyboard", 1_000)))

        clock.advance(Duration.ofHours(1))
        val original = PriceChanged(id, v2, 1_200)
        store.append("Product", id, 1, listOf(original))
        val t2 = clock.instant()

        clock.advance(Duration.ofDays(1))
        store.append("Product", id, 2, listOf(PriceChanged(id, v2, 1_150, correctionOf = original.eventId)))

        repo.stateAsOf(id, v2).price shouldBe 1_150
        repo.stateAsAt(id, t2).price shouldBe 1_200
        repo.stateAsAt(id, clock.instant()).price shouldBe 1_150

        val records = store.readStream(id)
        records.size shouldBe 3
        (records.single { it.eventId == original.eventId }.event as PriceChanged).price shouldBe 1_200
    }

    @Test
    fun `낙관적 동시성 - 어긋난 expectedSeqNo는 거부된다`() {
        val id = AggregateId.new()
        store.append("Product", id, 0, listOf(ProductRegistered(id, v1, "mouse", 500)))

        shouldThrow<OptimisticConcurrencyException> {
            store.append("Product", id, 0, listOf(PriceChanged(id, v2, 600)))
        }
    }

    @Test
    fun `DB에 저장된 v1 페이로드는 읽는 순간 v3로 업캐스트된다`() {
        val id = AggregateId.new()
        JdbcClient.create(dataSource).sql(
            """
            INSERT INTO event_store
                (cell_id, aggregate_type, aggregate_id, seq_no, event_id, event_type, event_version, payload, valid_time, transaction_time)
            VALUES
                (0, 'Order', :aggregateId, 1, :eventId, 'OrderPlaced', 1, :payload::jsonb, now(), now())
            """.trimIndent(),
        )
            .param("aggregateId", id.value)
            .param("eventId", UUID.randomUUID())
            .param("payload", """{"eventId":"01907e2e-0000-7000-8000-0000000000ff","aggregateId":"$id","validTime":"2026-01-01T00:00:00Z","correctionOf":null,"price":7700}""")
            .update()

        val event = store.readStream(id).single().event as OrderPlaced
        event.amount shouldBe 7_700
        event.currency shouldBe "KRW"
    }

    @Test
    fun `스냅샷 저장-로드 왕복 + 소급 정정 시 스냅샷 무효화 폴백`() {
        val id = AggregateId.new()
        val snapshots = PostgresSnapshotStore(dataSource, Product::class.java, serde.objectMapper)
        val snapshotting = SnapshottingAggregateRepository(ProductAggregate, store, snapshots)

        store.append("Product", id, 0, listOf(ProductRegistered(id, v1, "monitor", 300_000)))
        val original = PriceChanged(id, v2, 280_000)
        store.append("Product", id, 1, listOf(original))

        snapshots.save(Snapshot(id, seqNo = 2, state = repo.currentState(id), takenAt = clock.instant()))
        snapshots.load(id)!!.state.price shouldBe 280_000
        snapshotting.currentState(id).price shouldBe 280_000

        // 스냅샷 시점 이전 이벤트를 겨냥한 소급 정정 → 스냅샷을 버리고 전체 리플레이해야 한다
        store.append("Product", id, 2, listOf(PriceChanged(id, v2, 275_000, correctionOf = original.eventId)))
        snapshotting.currentState(id).price shouldBe 275_000
    }
}
