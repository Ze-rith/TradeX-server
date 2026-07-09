package io.chronos.tradex

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

/** 레거시 tradexServer의 계정 수명 주기 전체를 CHRONOS 재구현 위에서 재현하는 E2E. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "chronos.storage=IN_MEMORY",
        "chronos.ontology-dir=../ontology",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    ],
)
@TestMethodOrder(MethodOrderer.MethodName::class)
class AuthFlowE2ETest(@Autowired private val rest: TestRestTemplate) {
    private fun register(
        email: String,
        password: String = "Str0ng!Passw0rd",
        name: String = "김제리",
        birthDate: String = "1995-03-14",
        phone: String,
    ) = rest.postForEntity(
        "/api/v1/registration",
        mapOf("email" to email, "password" to password, "name" to name, "birthDate" to birthDate, "phoneNumber" to phone),
        Map::class.java,
    )

    private fun signIn(email: String, password: String) =
        rest.postForEntity("/api/v1/auth/sign-in", mapOf("email" to email, "password" to password), Map::class.java)

    @Suppress("UNCHECKED_CAST")
    private fun data(body: Map<*, *>?): Map<String, Any?> = body?.get("data") as Map<String, Any?>

    private fun refreshCookieOf(headers: HttpHeaders): String =
        headers[HttpHeaders.SET_COOKIE].orEmpty()
            .first { it.startsWith("refresh_token=") && !it.startsWith("refresh_token=;") && !it.startsWith("refresh_token=\"\"") }
            .substringBefore(";")

    @Test
    fun `t1 등록 - 성공하고 중복 이메일과 중복 전화는 409로 거절된다`() {
        val created = register(email = "dup-check@example.com", phone = "010-1111-2222")
        created.statusCode shouldBe HttpStatus.CREATED
        data(created.body)["userId"].shouldNotBeNull()

        register(email = "dup-check@example.com", phone = "010-9999-8888")
            .statusCode shouldBe HttpStatus.CONFLICT
        register(email = "other@example.com", phone = "+82 10-1111-2222") // 표기가 달라도 E.164 정규화로 잡힌다
            .statusCode shouldBe HttpStatus.CONFLICT
    }

    @Test
    fun `t2 등록 검증 - 약한 비밀번호와 미성년자는 400`() {
        register(email = "weak@example.com", password = "short", phone = "010-2222-3333")
            .statusCode shouldBe HttpStatus.BAD_REQUEST
        register(email = "weak2@example.com", password = "alllowercaseonly", phone = "010-2222-3334")
            .statusCode shouldBe HttpStatus.BAD_REQUEST
        register(email = "minor@example.com", birthDate = "2020-01-01", phone = "010-2222-3335")
            .statusCode shouldBe HttpStatus.BAD_REQUEST
    }

    @Test
    fun `t3 잠금 정책 - 5회 실패 후 올바른 비밀번호로도 423`() {
        register(email = "lockme@example.com", phone = "010-3333-4444").statusCode shouldBe HttpStatus.CREATED

        repeat(5) {
            signIn("lockme@example.com", "Wrong!Passw0rd").statusCode shouldBe HttpStatus.UNAUTHORIZED
        }
        signIn("lockme@example.com", "Str0ng!Passw0rd").statusCode shouldBe HttpStatus.LOCKED
    }

    @Test
    fun `t4 로그인 - 회전 - 재사용 감지 - 사인아웃 수명 주기`() {
        register(email = "lifecycle@example.com", phone = "010-4444-5555").statusCode shouldBe HttpStatus.CREATED

        // 로그인: access는 바디, refresh는 HttpOnly 쿠키
        val signedIn = signIn("lifecycle@example.com", "Str0ng!Passw0rd")
        signedIn.statusCode shouldBe HttpStatus.OK
        val access1 = data(signedIn.body)["accessToken"] as String
        val refreshCookie1 = refreshCookieOf(signedIn.headers)

        // 검증 엔드포인트
        val me = rest.exchange(
            "/api/v1/auth/me", HttpMethod.GET,
            HttpEntity<Void>(HttpHeaders().apply { setBearerAuth(access1) }), Map::class.java,
        )
        me.statusCode shouldBe HttpStatus.OK
        data(me.body)["userId"].shouldNotBeNull()

        // 회전: 새 쌍 발급 + 새 쿠키
        val reissued = rest.exchange(
            "/api/v1/auth/reissue", HttpMethod.POST,
            HttpEntity<Void>(HttpHeaders().apply { add(HttpHeaders.COOKIE, refreshCookie1) }), Map::class.java,
        )
        reissued.statusCode shouldBe HttpStatus.OK
        val access2 = data(reissued.body)["accessToken"] as String
        val refreshCookie2 = refreshCookieOf(reissued.headers)
        refreshCookie2 shouldNotBe refreshCookie1

        // 옛 refresh 재사용 → 재사용 감지로 401 (그리고 활성 세션도 방어적으로 폐기됨)
        rest.exchange(
            "/api/v1/auth/reissue", HttpMethod.POST,
            HttpEntity<Void>(HttpHeaders().apply { add(HttpHeaders.COOKIE, refreshCookie1) }), Map::class.java,
        ).statusCode shouldBe HttpStatus.UNAUTHORIZED

        // 재사용 감지 후에는 방금 회전된 refresh도 죽어 있어야 한다 (전면 폐기)
        rest.exchange(
            "/api/v1/auth/reissue", HttpMethod.POST,
            HttpEntity<Void>(HttpHeaders().apply { add(HttpHeaders.COOKIE, refreshCookie2) }), Map::class.java,
        ).statusCode shouldBe HttpStatus.UNAUTHORIZED

        // 다시 로그인 후 사인아웃 → access 블랙리스트
        val again = signIn("lifecycle@example.com", "Str0ng!Passw0rd")
        val access3 = data(again.body)["accessToken"] as String
        val refreshCookie3 = refreshCookieOf(again.headers)

        rest.exchange(
            "/api/v1/auth/sign-out", HttpMethod.POST,
            HttpEntity<Void>(
                HttpHeaders().apply {
                    setBearerAuth(access3)
                    add(HttpHeaders.COOKIE, refreshCookie3)
                },
            ),
            Map::class.java,
        ).statusCode shouldBe HttpStatus.OK

        // 사인아웃된 access로 /me → 401 (서명은 유효하지만 블랙리스트)
        rest.exchange(
            "/api/v1/auth/me", HttpMethod.GET,
            HttpEntity<Void>(HttpHeaders().apply { setBearerAuth(access3) }), Map::class.java,
        ).statusCode shouldBe HttpStatus.UNAUTHORIZED

        // access2는 사인아웃 대상이 아니었으므로 여전히 유효 (레거시와 동일: access는 개별 블랙리스트)
        rest.exchange(
            "/api/v1/auth/me", HttpMethod.GET,
            HttpEntity<Void>(HttpHeaders().apply { setBearerAuth(access2) }), Map::class.java,
        ).statusCode shouldBe HttpStatus.OK
    }
}
