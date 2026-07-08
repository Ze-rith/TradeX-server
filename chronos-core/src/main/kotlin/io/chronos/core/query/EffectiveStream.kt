package io.chronos.core.query

import io.chronos.core.event.DomainEvent
import io.chronos.core.event.EventId
import io.chronos.core.event.EventRecord

/**
 * 정정을 원본 위치로 접어 넣은 "유효 스트림" (DECISIONS.md D5).
 * 반환 레코드의 event는 최종 정정본, 위치(seqNo)는 원본의 것이다.
 * 정정의 정정은 체인을 끝까지 따른다. 입력 밖을 겨냥한 정정은 무시된다.
 */
internal fun effectiveStream(records: List<EventRecord<DomainEvent>>): List<EventRecord<DomainEvent>> {
    val latestCorrectionByTarget = HashMap<EventId, DomainEvent>()
    for (record in records) {
        record.event.correctionOf?.let { latestCorrectionByTarget[it] = record.event }
    }
    return records
        .filter { it.event.correctionOf == null }
        .map { original ->
            var resolved = original.event
            while (true) {
                resolved = latestCorrectionByTarget[resolved.eventId] ?: break
            }
            if (resolved === original.event) original else original.copy(event = resolved)
        }
}
