package io.tradex.core.event

import java.time.Instant

/**
 * 저장된 이벤트. 저장소가 부여하는 불변 메타데이터를 [event]에 덧씌운 봉투.
 *
 * [transactionTime]은 시스템이 이 이벤트를 안 시각이며 절대 변경되지 않는다.
 */
data class EventRecord<out E : DomainEvent>(
    val globalSeq: Long,
    val cellId: Int,
    val aggregateType: String,
    val seqNo: Long,
    val transactionTime: Instant,
    val event: E,
) {
    val eventId: EventId get() = event.eventId
    val aggregateId: AggregateId get() = event.aggregateId
}
