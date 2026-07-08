package com.zerith.tradexserver.auth.infrastructure.redis

import com.zerith.tradexserver.auth.domain.model.UserId
import com.zerith.tradexserver.auth.domain.repository.RefreshTokenStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RefreshTokenRedisStore(
    private val redisTemplate: StringRedisTemplate
) : RefreshTokenStore {

    override fun save(
        userId: UserId,
        jti: String,
        ttl: Duration
    ) {
        redisTemplate.opsForValue().set(
            refreshKey(userId),
            jti,
            ttl
        )
    }

    override fun findJtiByUserId(userId: UserId): String? {
        return redisTemplate.opsForValue().get(refreshKey(userId))
    }

    override fun delete(userId: UserId) {
        redisTemplate.delete(refreshKey(userId))
    }

    override fun blacklistJti(
        jti: String,
        ttl: Duration
    ) {
        redisTemplate.opsForValue().set(
            blacklistKey(jti),
            BLACKLIST_VALUE,
            ttl
        )
    }

    override fun isBlacklisted(jti: String): Boolean {
        val exists = redisTemplate.hasKey(blacklistKey(jti))
        return exists == true
    }

    private fun refreshKey(userId: UserId): String {
        return "$REFRESH_PREFIX${userId.asString()}"
    }

    private fun blacklistKey(jti: String): String {
        return "$BLACKLIST_PREFIX$jti"
    }

    companion object {
        private const val REFRESH_PREFIX = "auth:refresh:"
        private const val BLACKLIST_PREFIX = "auth:blacklist:jti:"
        private const val BLACKLIST_VALUE = "1"
    }
}