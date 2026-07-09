package io.tradex.core.query

import io.tradex.core.event.DomainEvent
import io.tradex.core.event.EventId
import io.tradex.core.event.EventRecord

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
