package com.zerith.tradexserver.auth.data

data class ReissueTokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long
) {
    companion object {
        fun of(
            accessToken: String,
            expiresIn: Long
        ): ReissueTokenResponse {
            return ReissueTokenResponse(accessToken, BEARER, expiresIn)
        }

        private const val BEARER = "Bearer"
    }
}