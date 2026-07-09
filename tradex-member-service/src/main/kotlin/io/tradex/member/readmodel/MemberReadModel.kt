package io.tradex.member.readmodel

import io.tradex.cell.CellFabric
import io.tradex.core.event.AggregateId
import io.tradex.core.event.DomainEvent
import io.tradex.core.event.EventRecord
import io.tradex.router.InMemoryProjectionOffsetStore
import io.tradex.router.Projection
import io.tradex.router.Projector
import io.tradex.member.contract.MemberCreated
import io.tradex.member.contract.MemberCreationRevoked
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import org.springframework.context.SmartLifecycle

/** 전화번호 HMAC 해시 유니크 인덱스 (DECISIONS.md D20). */
class PhoneIndexProjection : Projection {
    override val name = "phone-index"
    private val byHash = ConcurrentHashMap<String, AggregateId>()
    private val byMember = ConcurrentHashMap<AggregateId, String>()

    override fun apply(record: EventRecord<DomainEvent>) {
        when (val event = record.event) {
            is MemberCreated -> {
                byHash[event.phoneNumberHash] = record.aggregateId
                byMember[record.aggregateId] = event.phoneNumberHash
            }
            is MemberCreationRevoked -> byMember.remove(record.aggregateId)?.let(byHash::remove)
            else -> Unit
        }
    }

    fun memberIdOf(phoneNumberHash: String): AggregateId? = byHash[phoneNumberHash]
}

class MemberReadModel(fabric: CellFabric) : SmartLifecycle {
    val phoneIndex = PhoneIndexProjection()
    private val offsets = InMemoryProjectionOffsetStore()

    private val projectors: List<Projector> = fabric.cells.map { (cellId, cell) ->
        Projector(cell.store, CellScoped("phone-index-cell$cellId", phoneIndex), offsets, cellId)
    }

    fun catchUp() {
        projectors.forEach { projector ->
            while (projector.catchUpOnce() > 0) Unit
        }
    }

    private var running = false

    override fun start() {
        projectors.forEach { it.start(pollInterval = 20.milliseconds) }
        running = true
    }

    override fun stop() {
        projectors.forEach { it.stop() }
        running = false
    }

    override fun isRunning(): Boolean = running

    private class CellScoped(override val name: String, private val delegate: Projection) : Projection {
        override fun apply(record: EventRecord<DomainEvent>) = delegate.apply(record)
    }
}
