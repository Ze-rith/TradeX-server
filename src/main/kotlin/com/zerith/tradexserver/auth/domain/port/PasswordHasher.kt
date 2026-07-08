package com.zerith.tradexserver.auth.domain.port

import com.zerith.tradexserver.auth.domain.model.PasswordHash

interface PasswordHasher {

    fun hash(rawPassword: String): PasswordHash

    fun matches(
        rawPassword: String,
        passwordHash: PasswordHash
    ): Boolean
}