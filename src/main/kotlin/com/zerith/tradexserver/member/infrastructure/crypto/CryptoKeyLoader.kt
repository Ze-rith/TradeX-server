package com.zerith.tradexserver.member.infrastructure.crypto

import com.zerith.tradexserver.member.infrastructure.config.MemberCryptoProperties
import org.springframework.stereotype.Component
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Component
class CryptoKeyLoader(
    private val properties: MemberCryptoProperties
) {

    private val aesKeyCache: Map<Int, SecretKey> by lazy {
        properties.aesKeys.mapValues {
            decodeAesKey(it.value)
        }
    }

    private val hmacKeyCache: ByteArray by lazy {
        decodeHmacKey(properties.hmacKey)
    }

    fun activeKeyVersion(): Int {
        return properties.activeKeyVersion
    }

    fun aesKey(version: Int): SecretKey {
        return aesKeyCache[version]
            ?: throw IllegalStateException("invalid key version")
    }

    fun hmacKey(): ByteArray {
        return hmacKeyCache.copyOf()
    }

    private fun decodeAesKey(encoded: String): SecretKey {
        val bytes = Base64.getDecoder().decode(encoded)
        if (bytes.size != AES_KEY_BYTES) {
            throw IllegalStateException("invalid aes key length")
        }
        return SecretKeySpec(bytes, AES_ALGORITHM)
    }

    private fun decodeHmacKey(encoded: String): ByteArray {
        val bytes = Base64.getDecoder().decode(encoded)
        if (bytes.size < HMAC_MIN_BYTES) {
            throw IllegalStateException("invalid hmac key length")
        }
        return bytes
    }

    companion object {
        private const val AES_ALGORITHM = "AES"
        private const val AES_KEY_BYTES = 32
        private const val HMAC_MIN_BYTES = 32
    }
}