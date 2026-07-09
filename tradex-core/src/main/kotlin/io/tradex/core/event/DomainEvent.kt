package io.tradex.core.event

import java.time.Instant

/**
 * 모든 도메인 이벤트의 기반 계약.
 *
 * 애플리케이션은 자기 도메인에서 sealed 계층으로 이를 구현한다:
 * `sealed interface OrderEvent : DomainEvent`. (코어에서 sealed로 막지 않는 이유는 DECISIONS.md D4)
 *
 * - [validTime]: 사건이 실제 일어난 시각 (도메인이 부여).
 * - `transactionTime`(시스템이 안 시각)과 `seqNo`는 저장소가 부여하는 사실이므로
 *   [EventRecord] 봉투에 있다 (DECISIONS.md D3).
 * - [correctionOf]: 정정 이벤트면 원본 eventId. 원본은 절대 변경/삭제되지 않으며,
 *   리플레이 시 원본의 스트림 위치에서 이 이벤트의 내용으로 치환된다 (DECISIONS.md D5).
 */
interface DomainEvent {
    val eventId: EventId
    val aggregateId: AggregateId
    val validTime: Instant
    val correctionOf: EventId?
        get() = null
}
