package com.zerith.tradexserver.auth.application.command

class SignOutCommand private constructor(
    val rawAccessToken: String,
    val rawRefreshToken: String
) {
    companion object {
        fun of(
            rawAccessToken: String,
            rawRefreshToken: String
        ): SignOutCommand {
            return SignOutCommand(rawAccessToken, rawRefreshToken)
        }
    }
}