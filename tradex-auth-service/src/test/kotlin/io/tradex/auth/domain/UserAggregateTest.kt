package io.tradex.auth.domain

import io.tradex.core.event.AggregateId
import io.tradex.auth.contract.AccessTokenBlacklisted
import io.tradex.auth.contract.AuthEvent
import io.tradex.auth.contract.RefreshTokenIssued
import io.tradex.auth.contract.RefreshTokenRevoked
import io.tradex.auth.contract.SignInFailed
import io.tradex.auth.contract.SignInSucceeded
import io.tradex.auth.contract.UserRegistered
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Test

class UserAggregateTest {
    private val id = AggregateId.new()
    private val t = Instant.parse("2026-07-01T00:00:00Z")

    private fun replay(vararg events: AuthEvent): UserState =
        events.fold(UserAggregate.initial) { state, event -> UserAggregate.evolve(state, event) }

    @Test
    fun `잠금은 이벤트에 기록된 lockedUntil로만 판단하고 시간이 지나면 파생적으로 풀린다`() {
        val locked = replay(
            UserRegistered(id, t, "a@b.com", "hash"),
            SignInFailed(id, t, failureCount = 5, lockedUntil = t.plus(Duration.ofMinutes(30))),
        )

        locked.isLocked(t.plusSeconds(60)).shouldBeTrue()
        locked.isLocked(t.plus(Duration.ofMinutes(31))).shouldBeFalse()
    }

    @Test
    fun `로그인 성공은 실패 카운터와 잠금을 리셋한다`() {
        val state = replay(
            UserRegistered(id, t, "a@b.com", "hash"),
            SignInFailed(id, t, 3, null),
            SignInSucceeded(id, t),
        )
        state.failureCount shouldBe 0
        state.lockedUntil shouldBe null
    }

    @Test
    fun `refresh 회전 - 폐기는 활성 jti에만 작용한다`() {
        val base = replay(
            UserRegistered(id, t, "a@b.com", "hash"),
            RefreshTokenIssued(id, t, "jti-1", t.plus(Duration.ofDays(14))),
            RefreshTokenRevoked(id, t, "jti-1", "rotated"),
            RefreshTokenIssued(id, t, "jti-2", t.plus(Duration.ofDays(14))),
        )
        base.activeRefreshJti shouldBe "jti-2"

        UserAggregate.evolve(base, RefreshTokenRevoked(id, t, "jti-1", "stale")).activeRefreshJti shouldBe "jti-2"
    }

    @Test
    fun `블랙리스트는 만료 시각까지만 유효하다`() {
        val expiry = t.plus(Duration.ofMinutes(15))
        val state = replay(
            UserRegistered(id, t, "a@b.com", "hash"),
            AccessTokenBlacklisted(id, t, "jti-x", expiry),
        )
        state.isBlacklisted("jti-x", t.plusSeconds(60)).shouldBeTrue()
        state.isBlacklisted("jti-x", expiry.plusSeconds(1)).shouldBeFalse()
    }
}
