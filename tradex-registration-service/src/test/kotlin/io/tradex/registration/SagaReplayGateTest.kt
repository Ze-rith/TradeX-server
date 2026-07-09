package io.tradex.registration

import io.tradex.membrane.EventSchemaRegistry
import io.tradex.membrane.EventSerde
import io.tradex.membrane.UpcasterChain
import io.tradex.membrane.testkit.ReplayGate
import io.tradex.saga.engine.SagaEvent
import io.tradex.saga.engine.SagaReplayState
import io.tradex.registration.config.RegistrationWiring
import org.junit.jupiter.api.Test

class SagaReplayGateTest {
    @Test
    fun `등록된 전체 이벤트 스키마가 리플레이 게이트를 통과한다`() {
        val registry = EventSchemaRegistry()
        RegistrationWiring().sagaEventSchemas().contribute(registry)
        val serde = EventSerde(registry, UpcasterChain(emptyList()))

        val gate = ReplayGate(registry, serde, applyEvolve = { event ->
            SagaReplayState.from(listOf(event as SagaEvent))
        })

        gate.verify()
    }
}
