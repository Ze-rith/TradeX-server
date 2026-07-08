package com.zerith.tradexserver.auth.data

data class SignInResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long
) {
    companion object {
        fun of(
            accessToken: String,
            expiresIn: Long
        ): SignInResponse {
            return SignInResponse(accessToken, BEARER, expiresIn)
        }

        private const val BEARER = "Bearer"
    }
}