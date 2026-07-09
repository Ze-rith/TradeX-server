package io.tradex.router

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ConsistencyRouter(
    private val offsets: ProjectionOffsetStore,
    private val tokenCodec: SessionTokenCodec,
    private val waitTimeout: Duration = 2.seconds,
    private val pollInterval: Duration = 10.milliseconds,
) {

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
