package com.zerith.tradexserver.auth.application.command

class SignInCommand private constructor(
    val email: String,
    val rawPassword: String
) {
    companion object {
        fun of(
            email: String,
            rawPassword: String
        ): SignInCommand {
            return SignInCommand(email, rawPassword)
        }
    }
}