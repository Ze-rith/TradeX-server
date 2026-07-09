package io.chronos.example.order.api

import io.chronos.core.event.AggregateId
import io.chronos.example.order.OrderService
import io.chronos.example.order.PlaceOrderResult
import io.chronos.example.order.projection.OrderSummary
import io.chronos.router.ConsistencyLevel
import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class PlaceOrderRequest(val productName: String, val amount: Long, val currency: String = "KRW")
data class PriceCorrectionRequest(val amount: Long)
data class TokenResponse(val sessionToken: String)

@RestController
@RequestMapping("/orders")
class OrderController(private val service: OrderService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun place(@RequestBody request: PlaceOrderRequest): PlaceOrderResult =
        service.place(request.productName, request.amount, request.currency)

    @GetMapping("/{orderId}")
    fun read(
        @PathVariable orderId: String,
        @RequestHeader(name = "X-Consistency", required = false) consistency: String?,
        @RequestHeader(name = "X-Session-Token", required = false) sessionToken: String?,
    ): OrderSummary =
        service.read(AggregateId.of(orderId), ConsistencyLevel.fromHeader(consistency), sessionToken)

    @PostMapping("/{orderId}/price-correction")
    fun correctPrice(@PathVariable orderId: String, @RequestBody request: PriceCorrectionRequest): TokenResponse =
        TokenResponse(service.correctPrice(AggregateId.of(orderId), request.amount))

    /** bi-temporal: 정정이 소급 반영된 "그 시점(valid time)의 진실". */
    @GetMapping("/{orderId}/as-of")
    fun asOf(@PathVariable orderId: String, @RequestParam at: Instant): OrderSummary =
        service.stateAsOf(AggregateId.of(orderId), at)

    /** bi-temporal: "그 당시(transaction time) 시스템이 알던 모습" — 이후 정정 미반영. */
    @GetMapping("/{orderId}/as-at")
    fun asAt(@PathVariable orderId: String, @RequestParam at: Instant): OrderSummary =
        service.stateAsAt(AggregateId.of(orderId), at)
}
