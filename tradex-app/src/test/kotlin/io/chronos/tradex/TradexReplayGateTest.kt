package io.chronos.tradex

import io.chronos.membrane.EventSchemaRegistry
import io.chronos.membrane.EventSerde
import io.chronos.membrane.UpcasterChain
import io.chronos.membrane.testkit.ReplayGate
import io.chronos.saga.engine.SagaEvent
import io.chronos.saga.engine.SagaReplayState
import io.chronos.tradex.auth.contract.AuthEvent
import io.chronos.tradex.auth.domain.UserAggregate
import io.chronos.tradex.config.TradexWiring
import io.chronos.tradex.member.contract.MemberEvent
import io.chronos.tradex.member.domain.MemberAggregate
import org.junit.jupiter.api.Test

/** 이 앱이 저장하는 모든 (type, version)의 fixture 존재 + 로드→역직렬화→evolve 통과를 강제한다. */
class TradexReplayGateTest {
    @Test
    fun `등록된 전체 이벤트 스키마가 리플레이 게이트를 통과한다`() {
        val registry = EventSchemaRegistry()
        TradexWiring().tradexEventSchemas().contribute(registry)
        val serde = EventSerde(registry, UpcasterChain(emptyList()))

        var user = UserAggregate.initial
        var member = MemberAggregate.initial
        val gate = ReplayGate(registry, serde, applyEvolve = { event ->
            when (event) {
                is AuthEvent -> user = UserAggregate.evolve(user, event)
                is MemberEvent -> member = MemberAggregate.evolve(member, event)
                is SagaEvent -> SagaReplayState.from(listOf(event))
                else -> error("게이트에 알 수 없는 이벤트 타입: ${event::class}")
            }
        })

        gate.verify()
    }
}
