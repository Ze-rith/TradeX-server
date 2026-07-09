package io.chronos.tradex.registration

import io.chronos.membrane.EventSchemaRegistry
import io.chronos.membrane.EventSerde
import io.chronos.membrane.UpcasterChain
import io.chronos.membrane.testkit.ReplayGate
import io.chronos.saga.engine.SagaEvent
import io.chronos.saga.engine.SagaReplayState
import io.chronos.tradex.registration.config.RegistrationWiring
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
