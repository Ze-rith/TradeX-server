package io.tradex.member

import io.tradex.core.event.AggregateId
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "tradex.storage=IN_MEMORY",
        "tradex.ontology-dir=../ontology",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    ],
)
class MemberServiceE2ETest(@Autowired private val rest: TestRestTemplate) {
    @Suppress("UNCHECKED_CAST")
    private fun data(body: Map<*, *>?): Map<String, Any?> = body?.get("data") as Map<String, Any?>

    private fun prepare(name: String = "김제리", birthDate: String = "1995-03-14", phone: String) =
        rest.postForEntity(
            "/internal/members/prepare",
            mapOf("name" to name, "birthDate" to birthDate, "phoneNumber" to phone),
            Map::class.java,
        )

    @Test
    fun `프리페어는 PII를 암호문으로 돌려주고 검증 실패는 코드로 거절한다`() {
        val prepared = prepare(phone = "010-5555-6666")
        prepared.statusCode shouldBe HttpStatus.OK
        val body = data(prepared.body)
        body.keys shouldContain "encryptedName"
        body.keys shouldContain "phoneNumberHash"

        (body.values.none { it == "김제리" }) shouldBe true

        prepare(name = "!!!", phone = "010-5555-6667").let {
            it.statusCode shouldBe HttpStatus.BAD_REQUEST
            it.body?.get("code") shouldBe "INVALID_NAME"
        }
        prepare(birthDate = "2020-01-01", phone = "010-5555-6668").let {
            it.statusCode shouldBe HttpStatus.BAD_REQUEST
            it.body?.get("code") shouldBe "UNDERAGE"
        }
        prepare(phone = "12345").let {
            it.statusCode shouldBe HttpStatus.BAD_REQUEST
            it.body?.get("code") shouldBe "INVALID_PHONE"
        }
    }

    @Test
    fun `생성-중복-보상 수명 주기 - 표기가 달라도 E164 정규화로 중복을 잡는다`() {
        val prepared = data(prepare(phone = "010-7777-8888").body)
        val memberId = AggregateId.new().toString()

        val createBody = mapOf(
            "encryptedName" to prepared["encryptedName"],
            "encryptedBirthDate" to prepared["encryptedBirthDate"],
            "encryptedPhoneNumber" to prepared["encryptedPhoneNumber"],
            "phoneNumberHash" to prepared["phoneNumberHash"],
        )
        rest.exchange("/internal/members/$memberId", HttpMethod.PUT, HttpEntity(createBody), Map::class.java)
            .statusCode shouldBe HttpStatus.CREATED

        rest.exchange("/internal/members/$memberId", HttpMethod.PUT, HttpEntity(createBody), Map::class.java)
            .statusCode shouldBe HttpStatus.CREATED

        prepare(phone = "+82 10-7777-8888").let {
            it.statusCode shouldBe HttpStatus.CONFLICT
            it.body?.get("code") shouldBe "PHONE_DUPLICATE"
        }

        rest.exchange("/internal/members/$memberId?reason=test", HttpMethod.DELETE, HttpEntity.EMPTY, Map::class.java)
            .statusCode shouldBe HttpStatus.OK
        prepare(phone = "010-7777-8888").statusCode shouldBe HttpStatus.OK
    }
}
