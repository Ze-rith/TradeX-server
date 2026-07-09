package io.tradex.member.port

interface PiiCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}

interface PhoneNumberHasher {
    fun hash(e164: String): String
}
