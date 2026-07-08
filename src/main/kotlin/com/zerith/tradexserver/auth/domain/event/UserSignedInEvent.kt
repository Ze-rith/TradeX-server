package com.zerith.tradexserver.auth.domain.event

import com.zerith.tradexserver.auth.domain.model.UserId
import java.time.Instant

class UserSignedInEvent(
    val userId: UserId,
    val occurredAt: Instant
)