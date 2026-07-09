package io.chronos.example.order.api

import io.chronos.core.event.AggregateId
import io.chronos.example.order.EventView
import io.chronos.example.order.MigrationResult
import io.chronos.example.order.OrderService
import io.chronos.example.order.projection.OrderReadModel
import io.chronos.example.order.saga.FakePaymentPort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class PaymentModeRequest(val mode: FakePaymentPort.Mode)
data class ProjectionDelayRequest(val delayMs: Long)
data class MigrateRequest(val targetCell: Int)
data class StateHashResponse(val orderId: String, val cellId: Int, val stateHash: String)

@RestController
@RequestMapping("/admin")
class AdminController(
    private val service: OrderService,
    private val paymentPort: FakePaymentPort,
    private val readModel: OrderReadModel,
) {
    /** 사가 실패 유발 스위치: OK | FAIL(결제 거절) | TIMEOUT(step 타임아웃 유발). */
    @PostMapping("/payment-mode")
    fun paymentMode(@RequestBody request: PaymentModeRequest): Map<String, String> {
        paymentPort.mode = request.mode
        return mapOf("mode" to request.mode.name)
    }

    /** 프로젝션 인위적 지연 주입 — eventual 조회의 staleness를 눈으로 보는 용도. */
    @PostMapping("/projection-delay")
    fun projectionDelay(@RequestBody request: ProjectionDelayRequest): Map<String, Long> {
        readModel.artificialDelayMs = request.delayMs
        return mapOf("delayMs" to request.delayMs)
    }

    /** 셀 마이그레이션 트리거. 응답에 전후 상태 해시 비교가 포함된다. */
    @PostMapping("/orders/{orderId}/migrate")
    fun migrate(@PathVariable orderId: String, @RequestBody request: MigrateRequest): MigrationResult =
        service.migrate(AggregateId.of(orderId), request.targetCell)

    /** admin용 이벤트 스트림 조회 (정정·bi-temporal 메타 포함 원본 로우). */
    @GetMapping("/orders/{orderId}/events")
    fun events(@PathVariable orderId: String): List<EventView> = service.eventStream(AggregateId.of(orderId))

    @GetMapping("/orders/{orderId}/state-hash")
    fun stateHash(@PathVariable orderId: String): StateHashResponse {
        val id = AggregateId.of(orderId)
        return StateHashResponse(orderId, service.cellOf(id), service.stateHash(id))
    }
}
