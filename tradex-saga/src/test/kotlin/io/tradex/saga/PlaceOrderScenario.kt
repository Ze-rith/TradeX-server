package io.tradex.saga

import io.tradex.saga.dsl.saga
import io.tradex.saga.testkit.SagaScenario
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class OrderCtx(val orderId: String, val amount: Long)

class FakePaymentPort {
    var reserved = 0L
    var released = 0L
    val idempotencyKeys = mutableListOf<String>()

    fun reserve(key: String, amount: Long) {
        idempotencyKeys += key
        reserved += amount
    }

    fun release(key: String, amount: Long) {
        idempotencyKeys += key
        released += amount
    }
}

class FakeStockPort {
    var deducted = 0
    var restocked = 0

    fun deduct(key: String, quantity: Int) {
        deducted += quantity
    }

    fun restock(key: String, quantity: Int) {
        restocked += quantity
    }
}

class PlaceOrderScenario(compensationRetries: Int) : SagaScenario<OrderCtx> {
    val payment = FakePaymentPort()
    val stock = FakeStockPort()
    val shipments = mutableListOf<String>()

    override val context = OrderCtx(orderId = "order-1", amount = 10_000)

    override val definition = saga<OrderCtx>("PlaceOrder") {
        step("reservePayment") {
            action { payment.reserve(it.idempotencyKey, it.ctx.amount) }
            compensate { payment.release(it.idempotencyKey, it.ctx.amount) }
            timeout(5.seconds)
            retry(times = 3, backoff = exponential(200.milliseconds))
            compensationRetry(times = compensationRetries)
        }
        step("deductStock") {
            action { stock.deduct(it.idempotencyKey, 1) }
            compensate { stock.restock(it.idempotencyKey, 1) }
            timeout(3.seconds)
            retry(times = 2)
            compensationRetry(times = compensationRetries)
        }
        step("createShipment") {
            action { shipments += it.ctx.orderId }
            retry(times = 2)
        }
    }
}
