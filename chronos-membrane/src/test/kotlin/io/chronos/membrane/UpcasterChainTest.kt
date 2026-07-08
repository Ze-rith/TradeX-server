package io.chronos.membrane

import io.chronos.core.event.AggregateId
import io.chronos.core.event.EventId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.junit.jupiter.api.Test

/**
 * L1 인수 테스트: OrderPlaced v1(price) → v2(amount) → v3(+currency="KRW") 체인.
 */
class UpcasterChainTest {
    private val serde = orderSerde()

    private fun fixture(version: Int): String =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/OrderPlaced/v$version.json")).bufferedReader().readText()

    @Test
    fun `v1 JSON이 v3 객체로 승격된다 - price 개명 + currency 기본값`() {
        val event = serde.deserialize("OrderPlaced", 1, fixture(1)) as OrderPlaced

        event.amount shouldBe 15_000
        event.currency shouldBe "KRW"
        event.eventId shouldBe EventId.of("01907e2e-0000-7000-8000-000000000001")
        event.validTime shouldBe Instant.parse("2026-01-10T09:00:00Z")
    }

    @Test
    fun `v2 JSON은 currency 기본값만 주입된다`() {
        val event = serde.deserialize("OrderPlaced", 2, fixture(2)) as OrderPlaced
        event.amount shouldBe 22_000
        event.currency shouldBe "KRW"
    }

    @Test
    fun `최신 버전 JSON은 있는 그대로 역직렬화된다`() {
        val event = serde.deserialize("OrderPlaced", 3, fixture(3)) as OrderPlaced
        event.currency shouldBe "USD"
    }

    @Test
    fun `직렬화-역직렬화 왕복이 동일 객체를 복원한다`() {
        val original = OrderPlaced(AggregateId.new(), Instant.parse("2026-05-01T00:00:00Z"), amount = 500, currency = "EUR")
        val serialized = serde.serialize(original)

        serialized.type shouldBe "OrderPlaced"
        serialized.version shouldBe 3
        serde.deserialize(serialized.type, serialized.version, serialized.json) shouldBe original
    }

    @Test
    fun `체인에 결번이 있으면 조용히 통과시키지 않고 실패한다`() {
        val gapped = orderSerde(upcasters = listOf(OrderPlacedV2ToV3())) // v1→v2 없음

        shouldThrow<MissingUpcasterException> { gapped.deserialize("OrderPlaced", 1, fixture(1)) }
    }

    @Test
    fun `저장 버전이 코드 버전보다 높으면 실패한다 - 구버전 코드 방어`() {
        shouldThrow<IllegalArgumentException> { serde.deserialize("OrderPlaced", 4, fixture(3)) }
    }
}
