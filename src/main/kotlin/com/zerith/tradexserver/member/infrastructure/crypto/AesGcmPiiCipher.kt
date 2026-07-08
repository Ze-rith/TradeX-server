package com.zerith.tradexserver.member.infrastructure.crypto

import com.zerith.tradexserver.member.domain.model.EncryptedPii
import com.zerith.tradexserver.member.domain.port.PiiCipher
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

@Component
class AesGcmPiiCipher(
    private val keyLoader: CryptoKeyLoader
) : PiiCipher {

    private val secureRandom = SecureRandom()

    override fun encrypt(plaintext: String): EncryptedPii {
        val keyVersion = keyLoader.activeKeyVersion()
        val key = keyLoader.aesKey(keyVersion)

        val iv = ByteArray(IV_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            key,
            GCMParameterSpec(TAG_BITS, iv)
        )

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

        val encoded = Base64.getEncoder().encodeToString(combined)
        return EncryptedPii.of(encoded, keyVersion)
    }

    override fun decrypt(encrypted: EncryptedPii): String {
        val key = keyLoader.aesKey(encrypted.keyVersion)
        val combined = Base64.getDecoder().decode(encrypted.ciphertext)

        if (combined.size <= IV_BYTES) {
            throw IllegalStateException("invalid ciphertext")
        }

        val iv = combined.copyOfRange(0, IV_BYTES)
        val ciphertext = combined.copyOfRange(IV_BYTES, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(TAG_BITS, iv)
        )

        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext, Charsets.UTF_8)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}