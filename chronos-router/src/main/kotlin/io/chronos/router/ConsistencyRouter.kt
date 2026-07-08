package io.chronos.router

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 일관성 구배 라우터.
 *
 * - [ConsistencyLevel.EVENTUAL]: 프로젝션 즉시 조회 (stale 허용)
 * - [ConsistencyLevel.READ_YOUR_WRITES]: 세션 토큰의 seq까지 프로젝션 offset이 따라올 때까지
 *   짧은 폴링 대기. 타임아웃이면 [ProjectionLagTimeoutException] (웹 계층에서 503 + Retry-After).
 * - [ConsistencyLevel.STRONG]: 프로젝션을 거치지 않고 이벤트 스토어 직접 리플레이.
 */
class ConsistencyRouter(
    private val offsets: ProjectionOffsetStore,
    private val tokenCodec: SessionTokenCodec,
    private val waitTimeout: Duration = 2.seconds,
    private val pollInterval: Duration = 10.milliseconds,
) {
    /** 쓰기 응답에 실어줄 토큰. [lastGlobalSeq]는 방금 쓴 이벤트의 global_seq. */
    fun issueToken(lastGlobalSeq: Long): String = tokenCodec.encode(SessionToken(lastGlobalSeq))

    fun <R> read(
        level: ConsistencyLevel,
        projectionName: String,
        sessionToken: String?,
        strongRead: () -> R,
        projectionRead: () -> R,
    ): R = when (level) {
        ConsistencyLevel.EVENTUAL -> projectionRead()
        ConsistencyLevel.STRONG -> strongRead()
        ConsistencyLevel.READ_YOUR_WRITES -> {
            // 토큰이 없으면 이 세션에서 관측해야 할 쓰기도 없다 → eventual과 동일
            if (sessionToken == null) {
                projectionRead()
            } else {
                awaitProjection(projectionName, tokenCodec.decode(sessionToken).lastGlobalSeq)
                projectionRead()
            }
        }
    }

    private fun awaitProjection(projectionName: String, requiredSeq: Long) {
        val deadline = System.nanoTime() + waitTimeout.inWholeNanoseconds
        while (offsets.lastProcessed(projectionName) < requiredSeq) {
            if (System.nanoTime() >= deadline) {
                throw ProjectionLagTimeoutException(projectionName, requiredSeq, offsets.lastProcessed(projectionName), retryAfter = waitTimeout)
            }
            Thread.sleep(pollInterval.inWholeMilliseconds.coerceAtLeast(1))
        }
    }
}

class ProjectionLagTimeoutException(
    projectionName: String,
    requiredSeq: Long,
    actualSeq: Long,
    val retryAfter: Duration,
) : RuntimeException("프로젝션 '$projectionName' 지연: 필요 seq=$requiredSeq, 현재 seq=$actualSeq")
