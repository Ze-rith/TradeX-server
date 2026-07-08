package com.zerith.tradexserver.auth.domain.token

class TokenPair private constructor(
    val accessToken: AccessToken,
    val refreshToken: RefreshToken
) {
    companion object {
        fun of(
            accessToken: AccessToken,
            refreshToken: RefreshToken
        ): TokenPair {
            return TokenPair(accessToken, refreshToken)
        }
    }
}