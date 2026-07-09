package io.tradex.router

import io.tradex.core.event.AggregateId
import io.tradex.core.event.DomainEvent
import io.tradex.core.event.EventRecord
import io.tradex.core.query.AggregateRepository
import io.tradex.core.store.InMemoryEventStore
import io.tradex.core.testing.PriceChanged
import io.tradex.core.testing.ProductAggregate
import io.tradex.core.testing.ProductRegistered
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class ConsistencyGradientAcceptanceTest {
    private val store = InMemoryEventStore()
    private val projection = ProductPriceProjection()
    private val offsets = InMemoryProjectionOffsetStore()
    private val projector = Projector(
        store, projection, offsets,
        beforeApply = { Thread.sleep(500) },
    )
    private val router = ConsistencyRouter(offsets, SessionTokenCodec("secret".toByteArray()))
    private val repo = AggregateRepository(ProductAggregate, store)
    private val id = AggregateId.new()
    private val t = Instant.parse("2026-01-01T00:00:00Z")

    @AfterEach
    fun tearDown() = projector.stop()

    private fun read(level: ConsistencyLevel, token: String? = null): Long? =
        router.read(
            level = level,
            projectionName = projection.name,
            sessionToken = token,
            strongRead = { repo.currentState(id).price },
            projectionRead = { projection.priceOf(id) },
        )

    @Test
    fun `세 가지 일관성 수준이 각자의 계약을 지킨다`() {
        projector.start()

        store.append("Product", id, 0, listOf(ProductRegistered(id, t, "keyboard", 1_000)))
        awaitOffset(1)

        val written = store.append("Product", id, 1, listOf(PriceChanged(id, t, 2_000)))
        val token = router.issueToken(written.last().globalSeq)

        read(ConsistencyLevel.EVENTUAL) shouldBe 1_000

        read(ConsistencyLevel.STRONG) shouldBe 2_000

        read(ConsistencyLevel.READ_YOUR_WRITES, token) shouldBe 2_000
        offsets.lastProcessed(projection.name) shouldBe written.last().globalSeq
    }

    @Test
    fun `RYW - 프로젝션이 타임아웃 내에 못 따라오면 명시적 지연 예외 (503 + Retry-After 재료)`() {

        val impatientRouter = ConsistencyRouter(
            offsets, SessionTokenCodec("secret".toByteArray()),
            waitTimeout = 200.milliseconds,
        )
        store.append("Product", id, 0, listOf(ProductRegistered(id, t, "keyboard", 1_000)))
        val token = impatientRouter.issueToken(store.lastGlobalSeq())

        val exception = shouldThrow<ProjectionLagTimeoutException> {
            impatientRouter.read(
                ConsistencyLevel.READ_YOUR_WRITES, projection.name, token,
                strongRead = { error("호출되면 안 됨") },
                projectionRead = { projection.priceOf(id) },
            )
        }
        exception.retryAfter shouldBe 200.milliseconds
    }

    @Test
    fun `RYW - 토큰이 없으면 관측할 자기 쓰기도 없으므로 eventual처럼 동작한다`() {
        read(ConsistencyLevel.READ_YOUR_WRITES, token = null) shouldBe null
    }

    private fun awaitOffset(seq: Long) {
        val deadline = System.currentTimeMillis() + 5_000
        while (offsets.lastProcessed(projection.name) < seq) {
            check(System.currentTimeMillis() < deadline) { "프로젝션이 5초 내에 seq=$seq 에 도달하지 못함" }
            Thread.sleep(10)
        }
    }
}

class ProductPriceProjection : Projection {
    override val name = "product-price"
    private val prices = ConcurrentHashMap<AggregateId, Long>()

    override fun apply(record: EventRecord<DomainEvent>) {
        when (val event = record.event) {
            is ProductRegistered -> prices[event.aggregateId] = event.price
            is PriceChanged -> prices[event.aggregateId] = event.price
        }
    }

    fun priceOf(id: AggregateId): Long? = prices[id]
}
