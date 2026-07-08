package io.chronos.router

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SessionTokenTest {
    private val codec = SessionTokenCodec("test-secret-key".toByteArray())

    @Test
    fun `인코딩-디코딩 왕복이 성립한다`() {
        val encoded = codec.encode(SessionToken(42))
        codec.decode(encoded) shouldBe SessionToken(42)
    }

    @Test
    fun `seq를 위조하면 서명 불일치로 거부된다`() {
        val encoded = codec.encode(SessionToken(42))
        val tampered = "999." + encoded.substringAfter(".")

        shouldThrow<InvalidSessionTokenException> { codec.decode(tampered) }
    }

    @Test
    fun `다른 키로 서명된 토큰은 거부된다`() {
        val other = SessionTokenCodec("other-key".toByteArray())
        shouldThrow<InvalidSessionTokenException> { codec.decode(other.encode(SessionToken(1))) }
    }

    @Test
    fun `형식이 깨진 토큰은 거부된다`() {
        shouldThrow<InvalidSessionTokenException> { codec.decode("garbage") }
    }
}

class ConsistencyLevelTest {
    @Test
    fun `헤더 미지정은 eventual이 기본이다`() {
        ConsistencyLevel.fromHeader(null) shouldBe ConsistencyLevel.EVENTUAL
        ConsistencyLevel.fromHeader("") shouldBe ConsistencyLevel.EVENTUAL
    }

    @Test
    fun `세 가지 값을 대소문자 무관하게 파싱한다`() {
        ConsistencyLevel.fromHeader("strong") shouldBe ConsistencyLevel.STRONG
        ConsistencyLevel.fromHeader("Read-Your-Writes") shouldBe ConsistencyLevel.READ_YOUR_WRITES
        ConsistencyLevel.fromHeader("EVENTUAL") shouldBe ConsistencyLevel.EVENTUAL
    }

    @Test
    fun `모르는 값은 거부한다`() {
        shouldThrow<IllegalArgumentException> { ConsistencyLevel.fromHeader("linearizable") }
    }
}
