package com.zerith.tradexserver.auth.domain.repository

import com.zerith.tradexserver.auth.domain.model.UserId
import java.time.Duration

interface RefreshTokenStore {

    fun save(
        userId: UserId,
        jti: String,
        ttl: Duration
    )

    fun findJtiByUserId(userId: UserId): String?

    fun delete(userId: UserId)

    fun blacklistJti(
        jti: String,
        ttl: Duration
    )

    fun isBlacklisted(jti: String): Boolean
}