package io.tradex.auth

import io.tradex.membrane.EventSchemaRegistry
import io.tradex.membrane.EventSerde
import io.tradex.membrane.UpcasterChain
import io.tradex.membrane.testkit.ReplayGate
import io.tradex.auth.config.AuthWiring
import io.tradex.auth.contract.AuthEvent
import io.tradex.auth.domain.UserAggregate
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
