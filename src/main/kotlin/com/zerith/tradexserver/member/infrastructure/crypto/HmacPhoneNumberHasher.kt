package com.zerith.tradexserver.member.infrastructure.crypto

import com.zerith.tradexserver.member.domain.model.PhoneNumber
import com.zerith.tradexserver.member.domain.model.PhoneNumberHash
import com.zerith.tradexserver.member.domain.port.PhoneNumberHasher
import org.springframework.stereotype.Component
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class HmacPhoneNumberHasher(
    private val keyLoader: CryptoKeyLoader
) : PhoneNumberHasher {

    override fun hash(phoneNumber: PhoneNumber): PhoneNumberHash {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(keyLoader.hmacKey(), ALGORITHM))
        val raw = mac.doFinal(phoneNumber.e164.toByteArray(Charsets.UTF_8))
        return PhoneNumberHash.fromHashed(toHex(raw))
    }

    private fun toHex(bytes: ByteArray): String {
        val builder = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            builder.append(HEX[v ushr 4])
            builder.append(HEX[v and 0x0F])
        }
        return builder.toString()
    }

    companion object {
        private const val ALGORITHM = "HmacSHA256"
        private val HEX = "0123456789abcdef".toCharArray()
    }
}