package io.tradex.auth.readmodel

import io.tradex.cell.CellFabric
import io.tradex.core.event.AggregateId
import io.tradex.core.event.DomainEvent
import io.tradex.core.event.EventRecord
import io.tradex.router.InMemoryProjectionOffsetStore
import io.tradex.router.Projection
import io.tradex.router.Projector
import io.tradex.auth.contract.UserRegistered
import io.tradex.auth.contract.UserRegistrationRevoked
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import org.springframework.context.SmartLifecycle

class EmailIndexProjection : Projection {
    override val name = "email-index"
    private val byEmail = ConcurrentHashMap<String, AggregateId>()
    private val byUser = ConcurrentHashMap<AggregateId, String>()

    override fun apply(record: EventRecord<DomainEvent>) {
        when (val event = record.event) {
            is UserRegistered -> {
                byEmail[event.email] = record.aggregateId
                byUser[record.aggregateId] = event.email
            }
            is UserRegistrationRevoked -> byUser.remove(record.aggregateId)?.let(byEmail::remove)
            else -> Unit
        }
    }

    fun userIdOf(email: String): AggregateId? = byEmail[email]
}

class AuthReadModel(fabric: CellFabric) : SmartLifecycle {
    val emailIndex = EmailIndexProjection()
    private val offsets = InMemoryProjectionOffsetStore()

    private val projectors: List<Projector> = fabric.cells.map { (cellId, cell) ->
        Projector(cell.store, CellScoped("email-index-cell$cellId", emailIndex), offsets, cellId)
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
