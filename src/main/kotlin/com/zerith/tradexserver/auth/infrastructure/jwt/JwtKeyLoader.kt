package com.zerith.tradexserver.auth.infrastructure.jwt

import org.springframework.stereotype.Component
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

@Component
class JwtKeyLoader(
    private val jwtProperties: JwtProperties
) {

    fun loadPrivateKey(): PrivateKey {
        val der = decodePem(jwtProperties.privateKeyPem, PRIVATE_HEADER, PRIVATE_FOOTER)
        val keySpec = PKCS8EncodedKeySpec(der)
        val factory = KeyFactory.getInstance(ALGORITHM)
        return factory.generatePrivate(keySpec)
    }

    fun loadPublicKey(): PublicKey {
        val der = decodePem(jwtProperties.publicKeyPem, PUBLIC_HEADER, PUBLIC_FOOTER)
        val keySpec = X509EncodedKeySpec(der)
        val factory = KeyFactory.getInstance(ALGORITHM)
        return factory.generatePublic(keySpec)
    }

    private fun decodePem(
        pem: String,
        header: String,
        footer: String
    ): ByteArray {
        val sanitized = pem
            .replace(header, "")
            .replace(footer, "")
            .replace("\\s".toRegex(), "")
        return Base64.getDecoder().decode(sanitized)
    }

    companion object {
        private const val ALGORITHM = "RSA"
        private const val PRIVATE_HEADER = "-----BEGIN PRIVATE KEY-----"
        private const val PRIVATE_FOOTER = "-----END PRIVATE KEY-----"
        private const val PUBLIC_HEADER = "-----BEGIN PUBLIC KEY-----"
        private const val PUBLIC_FOOTER = "-----END PUBLIC KEY-----"
    }
}