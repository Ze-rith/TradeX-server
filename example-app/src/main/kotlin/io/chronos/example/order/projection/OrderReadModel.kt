package io.chronos.example.order.projection

import io.chronos.cell.CellFabric
import io.chronos.core.event.AggregateId
import io.chronos.core.event.DomainEvent
import io.chronos.core.event.EventRecord
import io.chronos.example.order.contract.OrderCancelled
import io.chronos.example.order.contract.OrderConfirmed
import io.chronos.example.order.contract.OrderPlaced
import io.chronos.router.InMemoryProjectionOffsetStore
import io.chronos.router.Projection
import io.chronos.router.ProjectionOffsetStore
import io.chronos.router.Projector
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import org.springframework.context.SmartLifecycle

data class OrderSummary(
    val orderId: String,
    val status: String,
    val productName: String?,
    val amount: Long?,
    val currency: String?,
)

class OrderSummaryProjection(override val name: String) : Projection {
    private val summaries = ConcurrentHashMap<AggregateId, OrderSummary>()

    override fun apply(record: EventRecord<DomainEvent>) {
        val id = record.aggregateId
        when (val event = record.event) {
            is OrderPlaced -> summaries[id] =
                OrderSummary(id.toString(), "PLACED", event.productName, event.amount, event.currency)
            is OrderConfirmed -> summaries.computeIfPresent(id) { _, s -> s.copy(status = "CONFIRMED") }
            is OrderCancelled -> summaries.computeIfPresent(id) { _, s -> s.copy(status = "CANCELLED") }
            else -> Unit // 사가 이벤트 등은 이 프로젝션의 관심사가 아니다
        }
    }

    fun summaryOf(id: AggregateId): OrderSummary? = summaries[id]
}

/**
 * 셀별 주문 프로젝션 + 프로젝터 묶음. [artificialDelayMs]는 README 데모용 지연 주입 스위치.
 */
class OrderReadModel(private val fabric: CellFabric) : SmartLifecycle {
    val offsets: ProjectionOffsetStore = InMemoryProjectionOffsetStore()

    @Volatile
    var artificialDelayMs: Long = 0

    private val projections: Map<Int, OrderSummaryProjection> = fabric.cells.mapValues { (cellId, _) ->
        OrderSummaryProjection("order-summary-cell$cellId")
    }
    private val projectors: List<Projector> = fabric.cells.map { (cellId, cell) ->
        Projector(
            store = cell.store,
            projection = projections.getValue(cellId),
            offsets = offsets,
            cellId = cellId,
            beforeApply = { if (artificialDelayMs > 0) Thread.sleep(artificialDelayMs) },
        )
    }

    private var running = false

    fun projectionNameFor(orderId: AggregateId): String = "order-summary-cell${fabric.cellIdFor(orderId)}"

    fun summaryOf(orderId: AggregateId): OrderSummary? =
        projections.getValue(fabric.cellIdFor(orderId)).summaryOf(orderId)

    override fun start() {
        projectors.forEach { it.start(pollInterval = 20.milliseconds) }
        running = true
    }

    override fun stop() {
        projectors.forEach { it.stop() }
        running = false
    }

    override fun isRunning(): Boolean = running
}
