package io.chronos.router

import io.chronos.core.event.AggregateId
import io.chronos.core.event.DomainEvent
import io.chronos.core.event.EventRecord
import io.chronos.core.query.AggregateRepository
import io.chronos.core.store.InMemoryEventStore
import io.chronos.core.testing.PriceChanged
import io.chronos.core.testing.ProductAggregate
import io.chronos.core.testing.ProductRegistered
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * L3 인수 테스트: 프로젝션 처리에 인위적 지연(500ms)을 주입한 상태에서
 * ① eventual은 쓰기 직후 옛 값을 볼 수 있다
 * ② read-your-writes는 항상 방금 쓴 값이 보인다
 * ③ strong은 프로젝션 지연과 무관하게 정확하다
 */
class ConsistencyGradientAcceptanceTest {
    private val store = InMemoryEventStore()
    private val projection = ProductPriceProjection()
    private val offsets = InMemoryProjectionOffsetStore()
    private val projector = Projector(
        store, projection, offsets,
        beforeApply = { Thread.sleep(500) }, // 인위적 프로젝션 지연
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

        // 초기 상태를 프로젝션에 반영시킨다
        store.append("Product", id, 0, listOf(ProductRegistered(id, t, "keyboard", 1_000)))
        awaitOffset(1)

        // 새 쓰기 — 프로젝션은 500ms 뒤에나 따라온다
        val written = store.append("Product", id, 1, listOf(PriceChanged(id, t, 2_000)))
        val token = router.issueToken(written.last().globalSeq)

        // ① eventual: 쓰기 직후에는 옛 값(1000)이 보일 수 있다 — stale 허용이 계약
        read(ConsistencyLevel.EVENTUAL) shouldBe 1_000

        // ③ strong: 프로젝션을 우회하므로 지연과 무관하게 정확
        read(ConsistencyLevel.STRONG) shouldBe 2_000

        // ② read-your-writes: 토큰의 seq까지 기다렸다가 반드시 자기 쓰기를 본다
        read(ConsistencyLevel.READ_YOUR_WRITES, token) shouldBe 2_000
        offsets.lastProcessed(projection.name) shouldBe written.last().globalSeq
    }

    @Test
    fun `RYW - 프로젝션이 타임아웃 내에 못 따라오면 명시적 지연 예외 (503 + Retry-After 재료)`() {
        // 프로젝터를 아예 켜지 않는다 = 무한 지연
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
