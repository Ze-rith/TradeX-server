package io.tradex.saga.engine

import io.tradex.core.event.AggregateId
import io.tradex.core.event.DomainEvent
import io.tradex.core.event.EventId
import io.tradex.membrane.EventSchema
import java.time.Instant

/**
 * 사가 엔진 자체가 이벤트소싱된다. 이 이벤트들이 L0 이벤트 스토어에 기록되며,
 * 어떤 사가 인스턴스든 이 히스토리만으로 결정론적 재구성이 가능하다 ([SagaReplayState]).
 */
sealed interface SagaEvent : DomainEvent {
    val sagaName: String
}

@EventSchema(type = "SagaStarted", version = 1)
data class SagaStarted(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    override val sagaName: String,
    val contextJson: String,
    override val eventId: EventId = EventId.new(),
) : SagaEvent

@EventSchema(type = "SagaStepSucceeded", version = 1)
data class StepSucceeded(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    override val sagaName: String,
    val stepName: String,
    val attempt: Int,
    override val eventId: EventId = EventId.new(),
) : SagaEvent

@EventSchema(type = "SagaStepFailed", version = 1)
data class StepFailed(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    override val sagaName: String,
    val stepName: String,
    val attempt: Int,
    val reason: String,
    override val eventId: EventId = EventId.new(),
) : SagaEvent

@EventSchema(type = "SagaStepTimedOut", version = 1)
data class StepTimedOut(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    override val sagaName: String,
    val stepName: String,
    val attempt: Int,
    override val eventId: EventId = EventId.new(),
) : SagaEvent

@EventSchema(type = "SagaCompensationStarted", version = 1)
data class CompensationStarted(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    override val sagaName: String,
    /** 보상을 촉발한(성공하지 못한) step. */
    val fromStepName: String,
    override val eventId: EventId = EventId.new(),
) : SagaEvent

@EventSchema(type = "SagaCompensationSucceeded", version = 1)
data class CompensationSucceeded(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    override val sagaName: String,
    val stepName: String,
    val attempt: Int,
    override val eventId: EventId = EventId.new(),
) : SagaEvent

@EventSchema(type = "SagaCompensationFailed", version = 1)
data class CompensationFailed(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    override val sagaName: String,
    val stepName: String,
    val attempt: Int,
    val reason: String,
    override val eventId: EventId = EventId.new(),
) : SagaEvent

@EventSchema(type = "SagaCompleted", version = 1)
data class SagaCompleted(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    override val sagaName: String,
    override val eventId: EventId = EventId.new(),
) : SagaEvent

@EventSchema(type = "SagaCompensated", version = 1)
data class SagaCompensated(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    override val sagaName: String,
    override val eventId: EventId = EventId.new(),
) : SagaEvent
