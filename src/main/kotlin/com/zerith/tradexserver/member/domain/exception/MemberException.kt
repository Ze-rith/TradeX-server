package com.zerith.tradexserver.member.domain.exception

sealed class MemberException(
    message: String
) : RuntimeException(message)