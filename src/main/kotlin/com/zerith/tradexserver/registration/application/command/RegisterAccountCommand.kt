package com.zerith.tradexserver.registration.application.command

import java.time.LocalDate

class RegisterAccountCommand private constructor(
    val email: String,
    val rawPassword: String,
    val name: String,
    val birthDate: LocalDate,
    val phoneNumber: String
) {
    companion object {
        fun of(
            email: String,
            rawPassword: String,
            name: String,
            birthDate: LocalDate,
            phoneNumber: String
        ): RegisterAccountCommand {
            return RegisterAccountCommand(email, rawPassword, name, birthDate, phoneNumber)
        }
    }
}