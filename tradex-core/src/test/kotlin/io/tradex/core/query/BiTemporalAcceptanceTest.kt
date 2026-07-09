package io.tradex.core.query

import io.tradex.core.event.AggregateId
import io.tradex.core.store.InMemoryEventStore
import io.tradex.core.store.InvalidCorrectionException
import io.tradex.core.testing.MutableClock
import io.tradex.core.testing.PriceChanged
import io.tradex.core.testing.ProductAggregate
import io.tradex.core.testing.ProductRegistered
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Test

/**
 * L0 인수 테스트: 가격 정정(retroactive correction) 시나리오.
 *
 * 타임라인
 * - validTime v1(1월 1일): 상품 등록, 가격 1000
 * - validTime v2(1월 5일): 가격 1200으로 변경 — 시스템은 T2에 기록
 * - T3(하루 뒤): "사실 v2 시점 가격은 1150이었다"는 소급 정정 기록
 */
class BiTemporalAcceptanceTest {
    private val v1 = Instant.parse("2026-01-01T00:00:00Z")
    private val v2 = Instant.parse("2026-01-05T00:00:00Z")

    private val clock = MutableClock(Instant.parse("2026-01-05T09:00:00Z"))
    private val store = InMemoryEventStore(clock)
    private val repo = AggregateRepository(ProductAggregate, store)
    private val id = AggregateId.new()

    private fun writeHistory(): Pair<PriceChanged, Instant> {
        store.append("Product", id, 0, listOf(ProductRegistered(id, v1, name = "keyboard", price = 1_000)))

        clock.advance(Duration.ofHours(1)) // T2
        val original = PriceChanged(id, v2, price = 1_200)
        store.append("Product", id, 1, listOf(original))
        val t2 = clock.instant()

        clock.advance(Duration.ofDays(1)) // T3: 소급 정정
        store.append("Product", id, 2, listOf(PriceChanged(id, v2, price = 1_150, correctionOf = original.eventId)))
        return original to t2
    }

    @Test
    fun `asOf 조회는 정정이 소급 반영된 그 시점의 진실을 본다`() {
        writeHistory()
        repo.stateAsOf(id, v2).price shouldBe 1_150
        repo.stateAsOf(id, v1).price shouldBe 1_000 // v2 이전은 정정과 무관
        repo.currentState(id).price shouldBe 1_150
    }

    @Test
    fun `asAt 조회는 그 당시 시스템이 알던 모습을 본다 - 정정 미반영`() {
        val (_, t2) = writeHistory()
        repo.stateAsAt(id, t2).price shouldBe 1_200          // 정정 기록 전의 지식
        repo.stateAsAt(id, clock.instant()).price shouldBe 1_150 // 정정 기록 후의 지식
    }

    @Test
    fun `원본 이벤트 로우는 물리적으로 보존된다`() {
        val (original, _) = writeHistory()
        val records = store.readStream(id)

        records.size shouldBe 3 // 등록 + 원본 변경 + 정정, 아무것도 삭제되지 않음
        val originalRecord = records.single { it.eventId == original.eventId }
        (originalRecord.event as PriceChanged).price shouldBe 1_200 // 페이로드 불변
        originalRecord.event.correctionOf shouldBe null
        originalRecord.seqNo shouldBe 2
    }

    @Test
    fun `정정의 정정은 체인 끝까지 따라간다`() {
        val (original, _) = writeHistory()
        val firstCorrection = store.readStream(id).last().event as PriceChanged

        clock.advance(Duration.ofDays(1))
        store.append(
            "Product", id, 3,
            listOf(PriceChanged(id, v2, price = 1_100, correctionOf = firstCorrection.eventId)),
        )

        repo.stateAsOf(id, v2).price shouldBe 1_100
        store.readStream(id).single { it.eventId == original.eventId } // 원본 여전히 존재
    }

    @Test
    fun `존재하지 않는 이벤트나 다른 타입에 대한 정정은 거부된다`() {
        val registered = ProductRegistered(id, v1, name = "keyboard", price = 1_000)
        store.append("Product", id, 0, listOf(registered))

        shouldThrow<InvalidCorrectionException> {
            store.append("Product", id, 1, listOf(PriceChanged(id, v2, 900, correctionOf = EventIdOf("00000000-0000-7000-8000-000000000000"))))
        }
        shouldThrow<InvalidCorrectionException> {
            // ProductRegistered를 PriceChanged로 정정 시도 → 타입 불일치
            store.append("Product", id, 1, listOf(PriceChanged(id, v2, 900, correctionOf = registered.eventId)))
        }
    }
}

private fun EventIdOf(value: String) = io.tradex.core.event.EventId.of(value)
