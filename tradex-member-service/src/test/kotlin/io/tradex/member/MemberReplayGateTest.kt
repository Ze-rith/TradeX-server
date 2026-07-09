package io.tradex.member

import io.tradex.membrane.EventSchemaRegistry
import io.tradex.membrane.EventSerde
import io.tradex.membrane.UpcasterChain
import io.tradex.membrane.testkit.ReplayGate
import io.tradex.member.config.MemberWiring
import io.tradex.member.contract.MemberEvent
import io.tradex.member.domain.MemberAggregate
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
