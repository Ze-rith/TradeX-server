package io.chronos.example

import io.chronos.example.config.AppWiring
import io.chronos.example.order.contract.OrderEvent
import io.chronos.example.order.domain.OrderAggregate
import io.chronos.membrane.EventSchemaRegistry
import io.chronos.membrane.EventSerde
import io.chronos.membrane.UpcasterChain
import io.chronos.membrane.testkit.ReplayGate
import io.chronos.saga.engine.SagaEvent
import io.chronos.saga.engine.SagaReplayState
import org.junit.jupiter.api.Test

/**
 * 리플레이 게이트: 이 앱이 저장하는 **모든** (type, version)에 fixture가 있고,
 * 로드→업캐스트→역직렬화→evolve까지 통과해야 빌드가 성공한다.
 * 새 이벤트나 새 버전을 추가하면 이 테스트가 fixture 작성을 강제한다.
 */
class ExampleReplayGateTest {
    @Test
    fun `등록된 전체 이벤트 스키마가 리플레이 게이트를 통과한다`() {
        val registry = EventSchemaRegistry()
        AppWiring().orderEventSchemas().contribute(registry)
        val serde = EventSerde(registry, UpcasterChain(emptyList()))

        var order = OrderAggregate.initial
        val gate = ReplayGate(registry, serde, applyEvolve = { event ->
            when (event) {
                is OrderEvent -> order = OrderAggregate.evolve(order, event)
                is SagaEvent -> SagaReplayState.from(listOf(event)) // fold 가능함을 증명
                else -> error("게이트에 알 수 없는 이벤트 타입: ${event::class}")
            }
        })

        gate.verify()
    }
}
