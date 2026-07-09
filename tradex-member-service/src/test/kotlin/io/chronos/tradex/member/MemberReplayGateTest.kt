package io.chronos.tradex.member

import io.chronos.membrane.EventSchemaRegistry
import io.chronos.membrane.EventSerde
import io.chronos.membrane.UpcasterChain
import io.chronos.membrane.testkit.ReplayGate
import io.chronos.tradex.member.config.MemberWiring
import io.chronos.tradex.member.contract.MemberEvent
import io.chronos.tradex.member.domain.MemberAggregate
import org.junit.jupiter.api.Test

class MemberReplayGateTest {
    @Test
    fun `등록된 전체 이벤트 스키마가 리플레이 게이트를 통과한다`() {
        val registry = EventSchemaRegistry()
        MemberWiring().memberEventSchemas().contribute(registry)
        val serde = EventSerde(registry, UpcasterChain(emptyList()))

        var member = MemberAggregate.initial
        val gate = ReplayGate(registry, serde, applyEvolve = { event ->
            member = MemberAggregate.evolve(member, event as MemberEvent)
        })

        gate.verify()
    }
}
