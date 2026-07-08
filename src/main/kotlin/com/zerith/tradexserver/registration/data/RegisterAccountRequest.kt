package com.zerith.tradexserver.registration.data

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class RegisterAccountRequest(

    @field:NotBlank
    @field:Email
    val email: String,

    @field:NotBlank
    @field:Size(min = 12, max = 128)
    val password: String,

    @field:NotBlank
    @field:Size(min = 1, max = 50)
    val name: String,

    @field:NotNull
    @field:JsonFormat(pattern = "yyyy-MM-dd")
    val birthDate: LocalDate,

    @field:NotBlank
    @field:Size(max = 20)
    val phoneNumber: String
)