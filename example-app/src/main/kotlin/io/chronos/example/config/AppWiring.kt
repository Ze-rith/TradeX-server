package io.chronos.example.config

import io.chronos.cell.CellFabric
import io.chronos.example.order.contract.OrderCancelled
import io.chronos.example.order.contract.OrderConfirmed
import io.chronos.example.order.contract.OrderPlaced
import io.chronos.example.order.projection.OrderReadModel
import io.chronos.example.order.saga.FakePaymentPort
import io.chronos.example.order.saga.FakeStockPort
import io.chronos.example.order.saga.OrderSagaCtx
import io.chronos.example.order.saga.ShipmentLog
import io.chronos.example.order.saga.placeOrderSaga
import io.chronos.router.ConsistencyRouter
import io.chronos.router.SessionTokenCodec
import io.chronos.runtime.ChronosProperties
import io.chronos.runtime.EventSchemaContributor
import io.chronos.saga.SagaDefinition
import io.chronos.saga.engine.CompensationFailed
import io.chronos.saga.engine.CompensationStarted
import io.chronos.saga.engine.CompensationSucceeded
import io.chronos.saga.engine.SagaCompensated
import io.chronos.saga.engine.SagaCompleted
import io.chronos.saga.engine.SagaStarted
import io.chronos.saga.engine.StepFailed
import io.chronos.saga.engine.StepSucceeded
import io.chronos.saga.engine.StepTimedOut
import kotlin.time.Duration.Companion.milliseconds
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AppWiring {
    /** 이 앱이 발행/저장하는 모든 이벤트 등록 — 온톨로지 가드와 serde의 재료. */
    @Bean
    fun orderEventSchemas(): EventSchemaContributor = EventSchemaContributor { registry ->
        registry.register(OrderPlaced::class)
        registry.register(OrderConfirmed::class)
        registry.register(OrderCancelled::class)

        // 사가 엔진 자체가 이벤트소싱되므로 사가 이벤트도 저장 대상이다
        registry.register(SagaStarted::class)
        registry.register(StepSucceeded::class)
        registry.register(StepFailed::class)
        registry.register(StepTimedOut::class)
        registry.register(CompensationStarted::class)
        registry.register(CompensationSucceeded::class)
        registry.register(CompensationFailed::class)
        registry.register(SagaCompleted::class)
        registry.register(SagaCompensated::class)
    }

    @Bean
    fun placeOrderSagaDefinition(
        payment: FakePaymentPort,
        stock: FakeStockPort,
        shipments: ShipmentLog,
    ): SagaDefinition<OrderSagaCtx> = placeOrderSaga(payment, stock, shipments)

    @Bean
    fun orderReadModel(fabric: CellFabric): OrderReadModel = OrderReadModel(fabric)

    @Bean
    fun consistencyRouter(readModel: OrderReadModel, codec: SessionTokenCodec, props: ChronosProperties): ConsistencyRouter =
        ConsistencyRouter(readModel.offsets, codec, waitTimeout = props.rywTimeoutMs.milliseconds)
}
