package io.chronos.core.store

import io.chronos.core.event.AggregateId
import io.chronos.core.event.DomainEvent
import io.chronos.core.event.EventRecord
import java.time.Instant

/**
 * Append-only bi-temporal 이벤트 스토어 포트.
 *
 * 계약:
 * - [append]는 낙관적 동시성 제어: [expectedSeqNo]가 스트림의 현재 마지막 seqNo와 다르면
 *   [OptimisticConcurrencyException]. 새 스트림은 expectedSeqNo = 0.
 * - 정정 이벤트(correctionOf != null)는 같은 스트림의 기존 이벤트를, 그리고 같은 이벤트
 *   타입만 가리킬 수 있다. 원본 로우는 절대 변경/삭제되지 않는다.
 * - transactionTime은 스토어가 부여하며 불변.
 */
interface EventStore {
    fun append(
        aggregateType: String,
        aggregateId: AggregateId,
        expectedSeqNo: Long,
        events: List<DomainEvent>,
    ): List<EventRecord<DomainEvent>>

    /** 스트림을 seqNo 오름차순으로. [afterSeqNo] 이후만 (스냅샷 이후 리플레이용). */
    fun readStream(aggregateId: AggregateId, afterSeqNo: Long = 0L): List<EventRecord<DomainEvent>>

    /** transactionTime ≤ [transactionTime] 인 레코드만 — "그 당시 시스템이 알던 모습"의 원료. */
    fun readStreamAsAt(aggregateId: AggregateId, transactionTime: Instant): List<EventRecord<DomainEvent>>

    /** globalSeq > [afterGlobalSeq] 인 레코드를 순서대로 (프로젝션/캐치업용). */
    fun readAll(afterGlobalSeq: Long = 0L, limit: Int = 1_000): List<EventRecord<DomainEvent>>

    /** 현재 최대 globalSeq. 비어 있으면 0. */
    fun lastGlobalSeq(): Long
}

class OptimisticConcurrencyException(
    aggregateId: AggregateId,
    expected: Long,
    actual: Long,
) : RuntimeException("aggregate=$aggregateId expectedSeqNo=$expected actualSeqNo=$actual")

class InvalidCorrectionException(message: String) : RuntimeException(message)
