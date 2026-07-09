package io.tradex.router

import io.tradex.core.event.DomainEvent
import io.tradex.core.event.EventRecord
import io.tradex.core.store.EventStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 이벤트 스토어를 폴링해 프로젝션을 따라잡게 하는 비동기 파이프라인 (단일 JVM 시뮬레이션).
 * [beforeApply]는 테스트가 인위적 프로젝션 지연을 주입하는 훅이다.
 */
class Projector(
    private val store: EventStore,
    private val projection: Projection,
    private val offsets: ProjectionOffsetStore,
    private val cellId: Int = 0,
    private val batchSize: Int = 256,
    private val beforeApply: (EventRecord<DomainEvent>) -> Unit = {},
) {
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null

    /** 대기 중인 이벤트를 한 번 처리하고 처리 건수를 반환한다. */
    fun catchUpOnce(): Int {
        val records = store.readAll(afterGlobalSeq = offsets.lastProcessed(projection.name), limit = batchSize)
        for (record in records) {
            beforeApply(record)
            projection.apply(record)
            offsets.update(projection.name, cellId, record.globalSeq)
        }
        return records.size
    }

    fun start(pollInterval: Duration = 20.milliseconds) {
        if (!running.compareAndSet(false, true)) return
        worker = Thread.ofVirtual().name("projector-${projection.name}").start {
            while (running.get()) {
                val processed = runCatching { catchUpOnce() }.getOrDefault(0)
                if (processed == 0) Thread.sleep(pollInterval.inWholeMilliseconds.coerceAtLeast(1))
            }
        }
    }

    fun stop() {
        running.set(false)
        worker?.join(1_000)
        worker = null
    }
}
