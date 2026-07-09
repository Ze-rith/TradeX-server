package io.tradex.auth

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.util.UUID

/** auth-service 단독 E2E: 내부 프로비저닝 API로 사용자를 만들고 인증 수명 주기를 검증한다. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "tradex.storage=IN_MEMORY",
        "tradex.ontology-dir=../ontology",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    ],
)
@TestMethodOrder(MethodOrderer.MethodName::class)
class AuthServiceE2ETest(@Autowired private val rest: TestRestTemplate) {
    @Suppress("UNCHECKED_CAST")
    private fun data(body: Map<*, *>?): Map<String, Any?> = body?.get("data") as Map<String, Any?>

    /** registration-service가 하는 일을 그대로: prepare → PUT users. */
    private fun provisionUser(email: String, password: String): String {
        val prepared = rest.postForEntity(
            "/internal/credentials",
            mapOf("email" to email, "password" to password),
            Map::class.java,
        )
        prepared.statusCode shouldBe HttpStatus.OK
        val hash = data(prepared.body)["passwordHash"] as String

        val userId = uuidV7String()
        rest.exchange(
            "/internal/users/$userId", HttpMethod.PUT,
            HttpEntity(mapOf("email" to email, "passwordHash" to hash)), Map::class.java,
        ).statusCode shouldBe HttpStatus.CREATED
        return userId
    }

    private fun signIn(email: String, password: String) =
        rest.postForEntity("/api/v1/auth/sign-in", mapOf("email" to email, "password" to password), Map::class.java)

    private fun refreshCookieOf(headers: HttpHeaders): String =
        headers[HttpHeaders.SET_COOKIE].orEmpty()
            .first { it.startsWith("refresh_token=") && !it.startsWith("refresh_token=;") }
            .substringBefore(";")

    @Test
    fun `t1 프로비저닝 - 중복 이메일 프리페어는 409, PUT은 멱등이다`() {
        val userId = provisionUser("prov@example.com", "Str0ng!Passw0rd")

        rest.postForEntity(
            "/internal/credentials",
            mapOf("email" to "prov@example.com", "password" to "Str0ng!Passw0rd"),
            Map::class.java,
        ).statusCode shouldBe HttpStatus.CONFLICT

        // 사가 재시도 시나리오: 같은 PUT 반복 → 멱등 (에러 없음)
        rest.exchange(
            "/internal/users/$userId", HttpMethod.PUT,
            HttpEntity(mapOf("email" to "prov@example.com", "passwordHash" to "whatever")), Map::class.java,
        ).statusCode shouldBe HttpStatus.CREATED
    }

    @Test
    fun `t2 보상 - DELETE로 폐기하면 이메일이 다시 사용 가능해진다`() {
        val userId = provisionUser("revoke-me@example.com", "Str0ng!Passw0rd")

        rest.exchange("/internal/users/$userId?reason=test", HttpMethod.DELETE, HttpEntity.EMPTY, Map::class.java)
            .statusCode shouldBe HttpStatus.OK
        // 멱등: 재폐기도 성공
        rest.exchange("/internal/users/$userId?reason=test", HttpMethod.DELETE, HttpEntity.EMPTY, Map::class.java)
            .statusCode shouldBe HttpStatus.OK

        // 폐기된 계정은 로그인 불가 + 이메일 재사용 가능
        signIn("revoke-me@example.com", "Str0ng!Passw0rd").statusCode shouldBe HttpStatus.UNAUTHORIZED
        rest.postForEntity(
            "/internal/credentials",
            mapOf("email" to "revoke-me@example.com", "password" to "Str0ng!Passw0rd"),
            Map::class.java,
        ).statusCode shouldBe HttpStatus.OK
    }

    @Test
    fun `t3 잠금 정책 - 5회 실패 후 정답도 423`() {
        provisionUser("lockme@example.com", "Str0ng!Passw0rd")
        repeat(5) { signIn("lockme@example.com", "Wrong!Passw0rd").statusCode shouldBe HttpStatus.UNAUTHORIZED }
        signIn("lockme@example.com", "Str0ng!Passw0rd").statusCode shouldBe HttpStatus.LOCKED
    }

    @Test
    fun `t4 로그인 - 회전 - 재사용 감지 - 사인아웃 수명 주기`() {
        provisionUser("lifecycle@example.com", "Str0ng!Passw0rd")

        val signedIn = signIn("lifecycle@example.com", "Str0ng!Passw0rd")
        signedIn.statusCode shouldBe HttpStatus.OK
        val access1 = data(signedIn.body)["accessToken"] as String
        val cookie1 = refreshCookieOf(signedIn.headers)

        val me = rest.exchange(
            "/api/v1/auth/me", HttpMethod.GET,
            HttpEntity<Void>(HttpHeaders().apply { setBearerAuth(access1) }), Map::class.java,
        )
        me.statusCode shouldBe HttpStatus.OK
        data(me.body)["userId"].shouldNotBeNull()

        val reissued = rest.exchange(
            "/api/v1/auth/reissue", HttpMethod.POST,
            HttpEntity<Void>(HttpHeaders().apply { add(HttpHeaders.COOKIE, cookie1) }), Map::class.java,
        )
        reissued.statusCode shouldBe HttpStatus.OK
        val cookie2 = refreshCookieOf(reissued.headers)
        cookie2 shouldNotBe cookie1

        // 옛 refresh 재사용 → 401 + 전면 폐기
        rest.exchange(
            "/api/v1/auth/reissue", HttpMethod.POST,
            HttpEntity<Void>(HttpHeaders().apply { add(HttpHeaders.COOKIE, cookie1) }), Map::class.java,
        ).statusCode shouldBe HttpStatus.UNAUTHORIZED
        rest.exchange(
            "/api/v1/auth/reissue", HttpMethod.POST,
            HttpEntity<Void>(HttpHeaders().apply { add(HttpHeaders.COOKIE, cookie2) }), Map::class.java,
        ).statusCode shouldBe HttpStatus.UNAUTHORIZED

        // 재로그인 → 사인아웃 → access 블랙리스트
        val again = signIn("lifecycle@example.com", "Str0ng!Passw0rd")
        val access3 = data(again.body)["accessToken"] as String
        val cookie3 = refreshCookieOf(again.headers)
        rest.exchange(
            "/api/v1/auth/sign-out", HttpMethod.POST,
            HttpEntity<Void>(
                HttpHeaders().apply {
                    setBearerAuth(access3)
                    add(HttpHeaders.COOKIE, cookie3)
                },
            ),
            Map::class.java,
        ).statusCode shouldBe HttpStatus.OK
        rest.exchange(
            "/api/v1/auth/me", HttpMethod.GET,
            HttpEntity<Void>(HttpHeaders().apply { setBearerAuth(access3) }), Map::class.java,
        ).statusCode shouldBe HttpStatus.UNAUTHORIZED
    }

    private fun uuidV7String(): String = io.tradex.core.event.AggregateId.new().toString()
}
