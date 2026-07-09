package io.chronos.tradex.member.infra

import io.chronos.tradex.member.port.PhoneNumberHasher
import io.chronos.tradex.member.port.PiiCipher
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "tradex.member.crypto")
data class MemberCryptoProperties(
    /** base64 인코딩 32바이트 AES 키. 데모 기본값 — 실제 배포라면 반드시 주입할 것. */
    val piiKeyBase64: String = "3q2+7wPQvL7yD5nB8kQxWm4t9JcAeUZR0oHs6fMignA=",
    /** 전화 유니크 인덱스용 HMAC-SHA256 키. */
    val phoneHmacKeyBase64: String = "cGhvbmUtaG1hYy1kZW1vLWtleS0zMmJ5dGVzLXBhZGRlZCE=",
)

/** 레거시 AesGcmPiiCipher 대체: AES-256-GCM, 페이로드 = base64(iv ‖ ciphertext). */
class AesGcmPiiCipher(props: MemberCryptoProperties) : PiiCipher {
    private val key = SecretKeySpec(Base64.getDecoder().decode(props.piiKeyBase64), "AES")
    private val random = SecureRandom()

    override fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_LENGTH).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    override fun decrypt(ciphertext: String): String {
        val decoded = Base64.getDecoder().decode(ciphertext)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, decoded.copyOfRange(0, IV_LENGTH)))
        return String(cipher.doFinal(decoded.copyOfRange(IV_LENGTH, decoded.size)), Charsets.UTF_8)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128
    }
}

/** 레거시 HmacPhoneNumberHasher 대체: 결정론적 HMAC-SHA256 → 유니크 인덱스 키. */
class HmacPhoneNumberHasher(props: MemberCryptoProperties) : PhoneNumberHasher {
    private val key = SecretKeySpec(Base64.getDecoder().decode(props.phoneHmacKeyBase64), "HmacSHA256")

    override fun hash(e164: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(key) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(e164.toByteArray(Charsets.UTF_8)))
    }
}
