package com.zerith.tradexserver.auth.domain.policy

import java.time.Duration

class LockPolicy private constructor(
    val failureThreshold: Int,
    val lockDuration: Duration
) {
    companion object {
        fun default(): LockPolicy {
            return LockPolicy(
                failureThreshold = 5,
                lockDuration = Duration.ofMinutes(30)
            )
        }
    }
}