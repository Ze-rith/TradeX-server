package io.tradex.ontology

import io.tradex.core.event.AggregateId
import io.tradex.core.event.DomainEvent
import io.tradex.core.event.EventId
import io.tradex.membrane.EventSchema
import io.tradex.membrane.EventSchemaRegistry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import java.time.Instant
import org.junit.jupiter.api.Test

@EventSchema(type = "OrderPlaced", version = 3)
private data class TestOrderPlaced(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val amount: Long,
    val currency: String,
    override val eventId: EventId = EventId.new(),
) : DomainEvent

@EventSchema(type = "StockReserved", version = 1)
private data class TestStockReserved(
    override val aggregateId: AggregateId,
    override val validTime: Instant,
    val quantity: Int,
    override val eventId: EventId = EventId.new(),
) : DomainEvent

class OntologyTest {
    private val codeRegistry = EventSchemaRegistry().apply {
        register(TestOrderPlaced::class)
        register(TestStockReserved::class)
    }

    private fun load(name: String): OntologyRegistry =
        OntologyRegistry.load(Path.of(javaClass.getResource("/$name")!!.toURI()))

    @Test
    fun `YAML 레지스트리를 로드한다 - 용어와 스키마`() {
        val ontology = load("ontology-approved")

        ontology.terms.single().term shouldBe "주문"
        ontology.schemaFor("OrderPlaced")!!.let {
            it.version shouldBe 3
            it.status shouldBe SchemaStatus.APPROVED
            it.fields shouldBe listOf("amount", "currency")
        }
    }

    @Test
    fun `전부 APPROVED이고 코드와 일치하면 가드를 통과한다`() {
        OntologyValidator.diff(load("ontology-approved"), codeRegistry).shouldBeEmpty()
        OntologyGuard.enforce(load("ontology-approved"), codeRegistry) // 예외 없음
    }

    @Test
    fun `DRAFT 스키마가 코드에 존재하면 기동 실패 - fail fast`() {
        val exception = shouldThrow<OntologyViolationException> {
            OntologyGuard.enforce(load("ontology-draft"), codeRegistry)
        }
        exception.message shouldContain "StockReserved"
        exception.message shouldContain "DRAFT"
    }

    @Test
    fun `버전 불일치와 양방향 누락을 diff가 잡아낸다`() {
        val problems = OntologyValidator.diff(load("ontology-mismatch"), codeRegistry)

        problems.size shouldBe 3
        problems.single { "버전 불일치" in it } shouldContain "OrderPlaced"
        problems.single { "미등록" in it } shouldContain "StockReserved"
        problems.single { "코드에 없음" in it } shouldContain "PaymentReserved"
    }
}
