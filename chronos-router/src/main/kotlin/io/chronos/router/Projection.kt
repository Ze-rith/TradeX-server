package io.chronos.router

import io.chronos.core.event.DomainEvent
import io.chronos.core.event.EventRecord

/** 이벤트 스트림에서 파생되는 읽기 모델. 언제든 offset 0부터 재구축 가능해야 한다. */
interface Projection {
    val name: String
    fun apply(record: EventRecord<DomainEvent>)
}

/** projection_offset 테이블의 포트: 프로젝션별 마지막 처리 global_seq. */
interface ProjectionOffsetStore {
    /** 처리 이력이 없으면 0. */
    fun lastProcessed(projectionName: String): Long
    fun update(projectionName: String, cellId: Int, globalSeq: Long)
}

class InMemoryProjectionOffsetStore : ProjectionOffsetStore {
    private val offsets = java.util.concurrent.ConcurrentHashMap<String, Long>()

    override fun lastProcessed(projectionName: String): Long = offsets[projectionName] ?: 0L

    override fun update(projectionName: String, cellId: Int, globalSeq: Long) {
        offsets[projectionName] = globalSeq
    }
}
