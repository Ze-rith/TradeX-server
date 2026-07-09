package io.chronos.example.order.saga

import io.chronos.saga.SagaDefinition
import io.chronos.saga.dsl.saga
import io.chronos.saga.exponential
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import org.springframework.stereotype.Component

data class OrderSagaCtx(val orderId: String, val amount: Long)

class PaymentDeclinedException(orderId: String) : RuntimeException("결제 거절: $orderId")

/**
 * 인메모리 fake 결제 포트. [mode] 스위치로 사가 실패를 유발한다 (admin API).
 * 멱등성 키를 저장해 재시도 중복 호출을 흡수한다.
 */
@Component
class FakePaymentPort {
    enum class Mode { OK, FAIL, TIMEOUT }

    @Volatile
    var mode: Mode = Mode.OK

    private val processedKeys = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    var reservedTotal: Long = 0

    @Volatile
    var releasedTotal: Long = 0

    fun reserve(idempotencyKey: String, orderId: String, amount: Long) {
        when (mode) {
            Mode.FAIL -> throw PaymentDeclinedException(orderId)
            Mode.TIMEOUT -> Thread.sleep(5_000) // step timeout(500ms)보다 길다 → StepTimedOut
            Mode.OK -> Unit
        }
        if (!processedKeys.add(idempotencyKey)) return // 멱등: 같은 키 재호출은 무시
        synchronized(this) { reservedTotal += amount }
    }

    fun release(idempotencyKey: String, orderId: String, amount: Long) {
        if (!processedKeys.add(idempotencyKey)) return
        synchronized(this) { releasedTotal += amount }
    }
}

@Component
class FakeStockPort {
    private val processedKeys = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    var stockRemaining: Int = 100

    fun deduct(idempotencyKey: String, quantity: Int) {
        if (!processedKeys.add(idempotencyKey)) return
        synchronized(this) { stockRemaining -= quantity }
    }

    fun restock(idempotencyKey: String, quantity: Int) {
        if (!processedKeys.add(idempotencyKey)) return
        synchronized(this) { stockRemaining += quantity }
    }
}

@Component
class ShipmentLog {
    val shipped = ConcurrentHashMap.newKeySet<String>()
}

/**
 * 주문 사가 정의. M3 모델 체커가 잡았던 결함을 반영해 모든 보상에 재시도(3회)가 붙어 있다.
 */
fun placeOrderSaga(
    payment: FakePaymentPort,
    stock: FakeStockPort,
    shipments: ShipmentLog,
): SagaDefinition<OrderSagaCtx> = saga("PlaceOrder") {
    step("reservePayment") {
        action { payment.reserve(it.idempotencyKey, it.ctx.orderId, it.ctx.amount) }
        compensate { payment.release(it.idempotencyKey, it.ctx.orderId, it.ctx.amount) }
        timeout(500.milliseconds)
        retry(times = 2, backoff = exponential(50.milliseconds))
        compensationRetry(times = 3)
    }
    step("deductStock") {
        action { stock.deduct(it.idempotencyKey, 1) }
        compensate { stock.restock(it.idempotencyKey, 1) }
        timeout(500.milliseconds)
        retry(times = 2)
        compensationRetry(times = 3)
    }
    step("createShipment") {
        action { shipments.shipped += it.ctx.orderId }
        retry(times = 2)
    }
}
