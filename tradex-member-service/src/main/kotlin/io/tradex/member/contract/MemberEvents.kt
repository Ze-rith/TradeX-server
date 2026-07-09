package io.tradex.member.contract

import io.tradex.core.event.AggregateId
import io.tradex.core.event.DomainEvent
import io.tradex.core.event.EventId
import io.tradex.membrane.EventSchema
import java.time.Instant

/** 멤버 Bounded Context. aggregateId = memberId = userId (레거시와 동일). */
sealed interface MemberEvent : DomainEvent

/**
 * PII(이름/생일/전화)는 **암호문으로만** 이벤트에 실린다 (DECISIONS.md D21).
 * 불변 로그에 평문 PII를 넣으면 삭제 요구에 대응할 수 없다 — 키 폐기(crypto-shredding)가 삭제 경로다.
 * 전화 유니크 제약은 HMAC 해시로만 검사한다.
 */
@EventSchema(type = "MemberCreated", version = 1)
data class MemberCreated(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val encryptedName: String,
    val encryptedBirthDate: String,
    val encryptedPhoneNumber: String,
    val phoneNumberHash: String,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : MemberEvent

/** 등록 사가의 보상. */
@EventSchema(type = "MemberCreationRevoked", version = 1)
data class MemberCreationRevoked(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val reason: String,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : MemberEvent
