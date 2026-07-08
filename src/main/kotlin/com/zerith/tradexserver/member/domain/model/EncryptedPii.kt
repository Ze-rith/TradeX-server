package com.zerith.tradexserver.member.domain.model

class EncryptedPii private constructor(
    val ciphertext: String,
    val keyVersion: Int
) {
    companion object {
        fun of(
            ciphertext: String,
            keyVersion: Int
        ): EncryptedPii {
            require(ciphertext.isNotBlank()) {
                "invalid ciphertext"
            }
            require(keyVersion > 0) {
                "invalid key version"
            }
            return EncryptedPii(ciphertext, keyVersion)
        }
    }

    override fun toString(): String {
        return "EncryptedPii(masked, v=$keyVersion)"
    }
}