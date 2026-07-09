package io.chronos.example

import io.chronos.core.event.AggregateId
import io.chronos.example.config.AppWiring
import io.chronos.membrane.EventSchemaRegistry
import io.chronos.membrane.EventSerde
import io.chronos.membrane.UpcasterChain
import io.chronos.saga.engine.SagaStarted
import io.chronos.saga.engine.StepFailed
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.junit.jupiter.api.Test

/**
 * 회귀 방지: 사가 이벤트는 correctionOf를 생성자 프로퍼티로 갖지 않는다(인터페이스 기본값).
 * 직렬화엔 포함되고 역직렬화 시엔 무시되어야 왕복이 성립한다 — Postgres 경로에서만 드러나던 버그.
 */
class SagaEventRoundTripTest {
    private val serde = run {
        val registry = EventSchemaRegistry()
        AppWiring().orderEventSchemas().contribute(registry)
        EventSerde(registry, UpcasterChain(emptyList()))
    }

    @Test
    fun `사가 이벤트 직렬화-역직렬화 왕복이 성립한다`() {
        val started = SagaStarted(AggregateId.new(), Instant.parse("2026-06-01T00:00:00Z"), "PlaceOrder", "{}")
        val failed = StepFailed(AggregateId.new(), Instant.parse("2026-06-01T00:00:01Z"), "PlaceOrder", "deductStock", 2, "declined")

        for (event in listOf(started, failed)) {
            val serialized = serde.serialize(event)
            serde.deserialize(serialized.type, serialized.version, serialized.json) shouldBe event
        }
    }
}
