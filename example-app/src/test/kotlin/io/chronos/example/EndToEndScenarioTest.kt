package io.chronos.example

import io.chronos.example.order.saga.FakePaymentPort
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

/** README curl 시나리오 전체를 코드로 재현하는 E2E. 인메모리 스토리지로 실행된다. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "chronos.storage=IN_MEMORY",
        "chronos.ontology-dir=../ontology",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    ],
)
class EndToEndScenarioTest(
    @Autowired private val rest: TestRestTemplate,
    @Autowired private val paymentPort: FakePaymentPort,
) {
    @AfterEach
    fun resetPaymentMode() {
        paymentPort.mode = FakePaymentPort.Mode.OK
    }

    private fun placeOrder(amount: Long = 150_000): Map<String, Any?> {
        val response = rest.postForEntity(
            "/orders",
            mapOf("productName" to "mechanical keyboard", "amount" to amount),
            Map::class.java,
        )
        response.statusCode shouldBe HttpStatus.CREATED
        @Suppress("UNCHECKED_CAST")
        return response.body as Map<String, Any?>
    }

    private fun readOrder(orderId: String, consistency: String? = null, token: String? = null): Map<String, Any?> {
        val headers = HttpHeaders().apply {
            consistency?.let { set("X-Consistency", it) }
            token?.let { set("X-Session-Token", it) }
        }
        val response = rest.exchange("/orders/$orderId", HttpMethod.GET, HttpEntity<Void>(headers), Map::class.java)
        response.statusCode shouldBe HttpStatus.OK
        @Suppress("UNCHECKED_CAST")
        return response.body as Map<String, Any?>
    }

    @Test
    fun `주문 생성 - 사가 완료 - read-your-writes 조회가 자기 쓰기를 본다`() {
        val placed = placeOrder()
        val orderId = placed["orderId"] as String
        placed["sagaOutcome"] shouldBe "COMPLETED"

        val view = readOrder(orderId, "read-your-writes", placed["sessionToken"] as String)
        view["status"] shouldBe "CONFIRMED"
        view["amount"] shouldBe 150_000

        readOrder(orderId, "strong")["status"] shouldBe "CONFIRMED"
    }

    @Test
    fun `가격 정정 - asOf는 소급 반영, asAt은 그 당시 지식, 원본 로우 보존`() {
        val orderId = placeOrder(amount = 150_000)["orderId"] as String
        Thread.sleep(20)
        val beforeCorrection = Instant.now()
        Thread.sleep(20)

        rest.postForEntity("/orders/$orderId/price-correction", mapOf("amount" to 135_000), Map::class.java)
            .statusCode shouldBe HttpStatus.OK

        // asOf: 정정이 소급 반영된 현재의 진실
        val asOf = rest.getForObject("/orders/$orderId/as-of?at=${Instant.now()}", Map::class.java)
        asOf["amount"] shouldBe 135_000

        // asAt(정정 이전): 그 당시 시스템이 알던 값
        val asAt = rest.getForObject("/orders/$orderId/as-at?at=$beforeCorrection", Map::class.java)
        asAt["amount"] shouldBe 150_000

        // 원본 이벤트 로우 물리 보존 + 정정 링크
        val events = rest.getForObject("/admin/orders/$orderId/events", List::class.java)
        @Suppress("UNCHECKED_CAST")
        val typed = events as List<Map<String, Any?>>
        typed.size shouldBe 3 // OrderPlaced + OrderConfirmed + 정정 OrderPlaced
        val correction = typed.single { it["correctionOf"] != null }
        correction["eventType"] shouldBe "OrderPlaced"
        val original = typed.single { it["eventId"] == correction["correctionOf"] }
        (original["payload"] as String).contains("150000").shouldBeTrue()
    }

    @Test
    fun `결제 실패 스위치 - 사가 보상 - 주문 CANCELLED + 사가 이벤트 히스토리 확인`() {
        rest.postForEntity("/admin/payment-mode", mapOf("mode" to "FAIL"), Map::class.java)

        val placed = placeOrder()
        placed["sagaOutcome"] shouldBe "COMPENSATED"
        placed["status"] shouldBe "CANCELLED"

        readOrder(placed["orderId"] as String, "strong")["status"] shouldBe "CANCELLED"

        // 사가 스트림 자체도 이벤트소싱되어 조회 가능하다
        val sagaEvents = rest.getForObject("/admin/orders/${placed["sagaId"]}/events", List::class.java)
        @Suppress("UNCHECKED_CAST")
        val types = (sagaEvents as List<Map<String, Any?>>).map { it["eventType"] }
        types.first() shouldBe "SagaStarted"
        types.last() shouldBe "SagaCompensated"
        (types.count { it == "SagaStepFailed" } >= 2).shouldBeTrue() // 재시도 소진 흔적
    }

    @Test
    fun `셀 마이그레이션 - 전후 상태 해시 동일`() {
        val placed = placeOrder()
        val orderId = placed["orderId"] as String

        val hashBefore = rest.getForObject("/admin/orders/$orderId/state-hash", Map::class.java)
        val fromCell = hashBefore["cellId"] as Int
        val targetCell = (fromCell + 1) % 3

        val migration = rest.postForObject(
            "/admin/orders/$orderId/migrate",
            mapOf("targetCell" to targetCell),
            Map::class.java,
        )
        migration["identical"] shouldBe true
        migration["fromCell"] shouldBe fromCell
        migration["toCell"] shouldBe targetCell
        (migration["copiedEvents"] as Int) shouldBeGreaterThanOrEqual 2

        val hashAfter = rest.getForObject("/admin/orders/$orderId/state-hash", Map::class.java)
        hashAfter["cellId"] shouldBe targetCell
        hashAfter["stateHash"] shouldBe hashBefore["stateHash"]

        readOrder(orderId, "strong")["status"] shouldBe "CONFIRMED"
    }

    @Test
    fun `모르는 일관성 헤더는 400, 없는 주문은 404`() {
        val headers = HttpHeaders().apply { set("X-Consistency", "linearizable") }
        rest.exchange("/orders/01907e2e-0000-7000-8000-000000000001", HttpMethod.GET, HttpEntity<Void>(headers), Map::class.java)
            .statusCode shouldBe HttpStatus.BAD_REQUEST

        rest.getForEntity("/orders/01907e2e-0000-7000-8000-000000000001", Map::class.java)
            .statusCode shouldNotBe HttpStatus.OK
    }
}
