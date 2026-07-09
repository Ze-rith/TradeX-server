package io.tradex.auth.contract

import io.tradex.core.event.AggregateId
import io.tradex.core.event.DomainEvent
import io.tradex.core.event.EventId
import io.tradex.membrane.EventSchema
import java.time.Instant

sealed interface AuthEvent : DomainEvent

@EventSchema(type = "UserRegistered", version = 1)
data class UserRegistered(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val email: String,

    val passwordHash: String,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : AuthEvent

@EventSchema(type = "UserRegistrationRevoked", version = 1)
data class UserRegistrationRevoked(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val reason: String,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : AuthEvent

@EventSchema(type = "SignInFailed", version = 1)
data class SignInFailed(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val failureCount: Int,
    val lockedUntil: Instant?,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : AuthEvent

@EventSchema(type = "SignInSucceeded", version = 1)
data class SignInSucceeded(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : AuthEvent

@EventSchema(type = "RefreshTokenIssued", version = 1)
data class RefreshTokenIssued(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val jti: String,
    val expiresAt: Instant,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : AuthEvent

@EventSchema(type = "RefreshTokenRevoked", version = 1)
data class RefreshTokenRevoked(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val jti: String,
    val reason: String,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : AuthEvent

@EventSchema(type = "AccessTokenBlacklisted", version = 1)
data class AccessTokenBlacklisted(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val jti: String,
    val expiresAt: Instant,
    override val eventId: EventId = EventId.new(),
    override val correctionOf: EventId? = null,
) : AuthEvent
