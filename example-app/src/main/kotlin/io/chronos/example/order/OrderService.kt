package io.chronos.example.order

import io.chronos.cell.CellFabric
import io.chronos.cell.CellMigrator
import io.chronos.core.event.AggregateId
import io.chronos.core.query.AggregateRepository
import io.chronos.core.store.EventStore
import io.chronos.example.order.contract.OrderPlaced
import io.chronos.example.order.domain.Order
import io.chronos.example.order.domain.OrderAggregate
import io.chronos.example.order.projection.OrderReadModel
import io.chronos.example.order.projection.OrderSummary
import io.chronos.example.order.saga.OrderSagaCtx
import io.chronos.membrane.EventSerde
import io.chronos.router.ConsistencyLevel
import io.chronos.router.ConsistencyRouter
import io.chronos.saga.SagaDefinition
import io.chronos.saga.engine.JacksonSagaContextCodec
import io.chronos.saga.engine.RealStepExecutor
import io.chronos.saga.engine.SagaOutcome
import java.security.MessageDigest
import java.time.Instant
import org.springframework.stereotype.Service

class OrderNotFoundException(orderId: String) : RuntimeException("주문 없음: $orderId")

data class PlaceOrderResult(
    val orderId: String,
    val sagaId: String,
    val sagaOutcome: SagaOutcome,
    val status: String,
    val sessionToken: String,
)

data class EventView(
    val globalSeq: Long,
    val cellId: Int,
    val seqNo: Long,
    val eventType: String,
    val eventId: String,
    val correctionOf: String?,
    val validTime: Instant,
    val transactionTime: Instant,
    val payload: String,
)

data class MigrationResult(
    val orderId: String,
    val fromCell: Int,
    val toCell: Int,
    val copiedEvents: Int,
    val stateHashBefore: String,
    val stateHashAfter: String,
    val identical: Boolean,
)

@Service
class OrderService(
    private val fabric: CellFabric,
    private val migrator: CellMigrator,
    private val readModel: OrderReadModel,
    private val router: ConsistencyRouter,
    private val sagaDefinition: SagaDefinition<OrderSagaCtx>,
    private val serde: EventSerde,
) {
    private val codec = JacksonSagaContextCodec(OrderSagaCtx::class.java)
    private val executor = RealStepExecutor<OrderSagaCtx>()

    fun place(productName: String, amount: Long, currency: String): PlaceOrderResult {
        val orderId = AggregateId.new()
        val cell = fabric.cellFor(orderId)
        fabric.append("Order", orderId, 0, listOf(OrderPlaced(orderId, Instant.now(), productName, amount, currency)))

        // 사가 인스턴스도 하나의 aggregate — 자기 해시에 따라 셀에 배치되고 admin API로 조회 가능
        val sagaId = AggregateId.new()
        val outcome = fabric.cellFor(sagaId).sagaEngine(sagaDefinition, executor, codec)
            .start(sagaId, OrderSagaCtx(orderId.toString(), amount))

        val followUp = when (outcome) {
            SagaOutcome.COMPLETED ->
                io.chronos.example.order.contract.OrderConfirmed(orderId, Instant.now())
            else ->
                io.chronos.example.order.contract.OrderCancelled(orderId, Instant.now(), reason = "saga $outcome")
        }
        fabric.append("Order", orderId, 1, listOf(followUp))

        return PlaceOrderResult(
            orderId = orderId.toString(),
            sagaId = sagaId.toString(),
            sagaOutcome = outcome,
            status = if (outcome == SagaOutcome.COMPLETED) "CONFIRMED" else "CANCELLED",
            sessionToken = router.issueToken(cell.store.lastGlobalSeq()),
        )
    }

    fun read(orderId: AggregateId, level: ConsistencyLevel, sessionToken: String?): OrderSummary =
        router.read(
            level = level,
            projectionName = readModel.projectionNameFor(orderId),
            sessionToken = sessionToken,
            strongRead = { currentState(orderId).toSummary(orderId) },
            projectionRead = { readModel.summaryOf(orderId) ?: throw OrderNotFoundException(orderId.toString()) },
        )

    /** 가격 정정: 원본 OrderPlaced의 valid-time을 유지한 채 소급 수정한다. */
    fun correctPrice(orderId: AggregateId, newAmount: Long): String {
        val stream = fabric.readStream(orderId).ifEmpty { throw OrderNotFoundException(orderId.toString()) }
        // 정정의 정정을 허용: 가장 최근의 OrderPlaced(정정 포함)를 대상으로 삼는다
        val target = stream.map { it.event }.filterIsInstance<OrderPlaced>().last()
        val correction = target.copy(
            amount = newAmount,
            eventId = io.chronos.core.event.EventId.new(),
            correctionOf = target.eventId,
        )
        fabric.append("Order", orderId, stream.last().seqNo, listOf(correction))
        return router.issueToken(fabric.cellFor(orderId).store.lastGlobalSeq())
    }

    fun stateAsOf(orderId: AggregateId, validTime: Instant): OrderSummary =
        repository(orderId).stateAsOf(orderId, validTime).toSummary(orderId)

    fun stateAsAt(orderId: AggregateId, transactionTime: Instant): OrderSummary =
        repository(orderId).stateAsAt(orderId, transactionTime).toSummary(orderId)

    fun migrate(orderId: AggregateId, targetCell: Int): MigrationResult {
        val before = stateHash(orderId)
        val report = migrator.migrate(orderId, targetCell)
        val after = stateHash(orderId)
        return MigrationResult(
            orderId = orderId.toString(),
            fromCell = report.fromCellId,
            toCell = report.toCellId,
            copiedEvents = report.copiedEvents,
            stateHashBefore = before,
            stateHashAfter = after,
            identical = before == after,
        )
    }

    fun stateHash(orderId: AggregateId): String {
        val state = currentState(orderId)
        return MessageDigest.getInstance("SHA-256").digest(state.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun cellOf(orderId: AggregateId): Int = fabric.cellIdFor(orderId)

    fun eventStream(orderId: AggregateId): List<EventView> =
        fabric.readStream(orderId).map { record ->
            val serialized = serde.serialize(record.event)
            EventView(
                globalSeq = record.globalSeq,
                cellId = record.cellId,
                seqNo = record.seqNo,
                eventType = serialized.type,
                eventId = record.eventId.toString(),
                correctionOf = record.event.correctionOf?.toString(),
                validTime = record.event.validTime,
                transactionTime = record.transactionTime,
                payload = serialized.json,
            )
        }

    private fun currentState(orderId: AggregateId): Order {
        val state = repository(orderId).currentState(orderId)
        if (state.status == null) throw OrderNotFoundException(orderId.toString())
        return state
    }

    private fun repository(orderId: AggregateId): AggregateRepository<Order, io.chronos.example.order.contract.OrderEvent> =
        AggregateRepository(OrderAggregate, orderScopedStore(orderId))

    private fun orderScopedStore(orderId: AggregateId): EventStore = fabric.cellFor(orderId).store

    private fun Order.toSummary(orderId: AggregateId): OrderSummary =
        OrderSummary(orderId.toString(), status?.name ?: "NONE", productName, amount, currency)
}
