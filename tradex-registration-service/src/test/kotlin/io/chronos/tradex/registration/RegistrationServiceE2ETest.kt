package io.chronos.tradex.registration

import io.chronos.tradex.registration.port.MemberProvisioningPort
import io.chronos.tradex.registration.port.UserProvisioningPort
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpStatus

/**
 * registration-service 단독 E2E. 다운스트림은 fake 포트로 대체한다
 * (실제 3서비스 연동은 README의 curl 시나리오로 검증).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "chronos.storage=IN_MEMORY",
        "chronos.ontology-dir=../ontology",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    ],
)
class RegistrationServiceE2ETest(
    @Autowired private val rest: TestRestTemplate,
    @Autowired private val users: UserProvisioningPort,
    @Autowired private val members: MemberProvisioningPort,
) {
    @TestConfiguration
    class Fakes {
        @Bean
        @Primary
        fun fakeUsers(): UserProvisioningPort = FakeUserPort()

        @Bean
        @Primary
        fun fakeMembers(): MemberProvisioningPort = FakeMemberPort()
    }

    private fun register(email: String, phone: String) = rest.postForEntity(
        "/api/v1/registration",
        mapOf(
            "email" to email,
            "password" to "Str0ng!Passw0rd",
            "name" to "김제리",
            "birthDate" to "1995-03-14",
            "phoneNumber" to phone,
        ),
        Map::class.java,
    )

    @Test
    fun `등록 성공 - 사가가 User와 Member를 모두 만든다`() {
        val response = register("saga-e2e@example.com", "010-1234-0001")

        response.statusCode shouldBe HttpStatus.CREATED
        @Suppress("UNCHECKED_CAST")
        val userId = (response.body?.get("data") as Map<String, Any?>)["userId"] as String
        userId.shouldNotBeNull()
        (users as FakeUserPort).userExists(userId) shouldBe true
        (members as FakeMemberPort).memberExists(userId) shouldBe true
    }

    @Test
    fun `다운스트림 거절은 status와 code가 그대로 전달된다`() {
        register("dup@example.com", "010-1234-0002").statusCode shouldBe HttpStatus.CREATED

        register("dup@example.com", "010-1234-0003").let {
            it.statusCode shouldBe HttpStatus.CONFLICT
            it.body?.get("code") shouldBe "EMAIL_DUPLICATE"
        }
        register("other@example.com", "010-1234-0002").let {
            it.statusCode shouldBe HttpStatus.CONFLICT
            it.body?.get("code") shouldBe "PHONE_DUPLICATE"
        }
    }
}
