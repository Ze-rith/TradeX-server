package com.zerith.tradexserver.auth.application.command

class ValidateAccessTokenCommand private constructor(
    val rawAccessToken: String
) {
    companion object {
        fun of(rawAccessToken: String): ValidateAccessTokenCommand {
            return ValidateAccessTokenCommand(rawAccessToken)
        }
    }
}