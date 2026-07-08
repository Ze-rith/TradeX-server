package com.zerith.tradexserver.member.domain.port

import com.zerith.tradexserver.member.domain.model.EncryptedPii

interface PiiCipher {

    fun encrypt(plaintext: String): EncryptedPii

    fun decrypt(encrypted: EncryptedPii): String
}