package io.tradex.member.contract

import io.tradex.core.event.AggregateId
import io.tradex.core.event.DomainEvent
import io.tradex.core.event.EventId
import io.tradex.membrane.EventSchema
import java.time.Instant

sealed interface MemberEvent : DomainEvent

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

@EventSchema(type = "MemberCreationRevoked", version = 1)
data class MemberCreationRevoked(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val reason: String,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : MemberEvent
