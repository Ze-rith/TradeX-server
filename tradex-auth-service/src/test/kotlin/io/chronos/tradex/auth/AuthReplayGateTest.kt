package io.chronos.tradex.auth

import io.chronos.membrane.EventSchemaRegistry
import io.chronos.membrane.EventSerde
import io.chronos.membrane.UpcasterChain
import io.chronos.membrane.testkit.ReplayGate
import io.chronos.tradex.auth.config.AuthWiring
import io.chronos.tradex.auth.contract.AuthEvent
import io.chronos.tradex.auth.domain.UserAggregate
import org.junit.jupiter.api.Test

class AuthReplayGateTest {
    @Test
    fun `등록된 전체 이벤트 스키마가 리플레이 게이트를 통과한다`() {
        val registry = EventSchemaRegistry()
        AuthWiring().authEventSchemas().contribute(registry)
        val serde = EventSerde(registry, UpcasterChain(emptyList()))

        var user = UserAggregate.initial
        val gate = ReplayGate(registry, serde, applyEvolve = { event ->
            user = UserAggregate.evolve(user, event as AuthEvent)
        })

        gate.verify()
    }
}
