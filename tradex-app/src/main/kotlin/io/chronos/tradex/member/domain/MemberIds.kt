package io.chronos.tradex.member.domain

import io.chronos.core.event.AggregateId
import java.util.UUID

/**
 * 레거시는 memberId = userId였지만, 이벤트소싱에서는 같은 aggregateId를 쓰면
 * User/Member 이벤트가 한 스트림에 섞여 각자의 리플레이가 깨진다.
 * userId에서 **결정론적으로 유도**한 별도 스트림 ID를 쓴다 — API 표면의 memberId는 여전히 userId.
 */
object MemberIds {
    fun of(userId: AggregateId): AggregateId =
        AggregateId(UUID.nameUUIDFromBytes("tradex-member:$userId".toByteArray(Charsets.UTF_8)))
}
