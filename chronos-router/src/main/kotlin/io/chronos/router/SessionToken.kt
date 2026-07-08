package io.chronos.router

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 쓰기 응답에 발급되는 세션 토큰: 클라이언트가 마지막으로 관측해야 하는 global_seq를 담는다.
 * HMAC-SHA256 서명으로 클라이언트 위조(더 큰 seq 요구 → DoS, 더 작은 seq → stale 읽기 허용)를 막는다.
 */
data class SessionToken(val lastGlobalSeq: Long)

class SessionTokenCodec(secret: ByteArray) {
    private val keySpec = SecretKeySpec(secret, ALGORITHM)

    fun encode(token: SessionToken): String {
        val payload = token.lastGlobalSeq.toString()
        return "$payload.${Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload))}"
    }

    fun decode(encoded: String): SessionToken {
        val parts = encoded.split(".")
        if (parts.size != 2) throw InvalidSessionTokenException("형식 오류")
        val (payload, signature) = parts
        val expected = sign(payload)
        val actual = runCatching { Base64.getUrlDecoder().decode(signature) }
            .getOrElse { throw InvalidSessionTokenException("서명 디코딩 실패") }
        if (!java.security.MessageDigest.isEqual(expected, actual)) {
            throw InvalidSessionTokenException("서명 불일치")
        }
        val seq = payload.toLongOrNull() ?: throw InvalidSessionTokenException("seq 파싱 실패")
        return SessionToken(seq)
    }

    private fun sign(payload: String): ByteArray =
        Mac.getInstance(ALGORITHM).apply { init(keySpec) }.doFinal(payload.toByteArray())

    companion object {
        private const val ALGORITHM = "HmacSHA256"
    }
}

class InvalidSessionTokenException(message: String) : RuntimeException("세션 토큰 무효: $message")
