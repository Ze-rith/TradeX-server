package com.zerith.tradexserver.auth.domain.exception

sealed class AuthException(
    message: String
) : RuntimeException(message)