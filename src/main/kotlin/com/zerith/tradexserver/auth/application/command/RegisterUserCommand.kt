package com.zerith.tradexserver.auth.application.command

class RegisterUserCommand private constructor(
    val email: String,
    val rawPassword: String
) {
    companion object {
        fun of(
            email: String,
            rawPassword: String
        ): RegisterUserCommand {
            return RegisterUserCommand(email, rawPassword)
        }
    }
}