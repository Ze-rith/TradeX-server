package com.zerith.tradexserver.auth.data

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class SignInRequest(

    @field:NotBlank
    @field:Email
    val email: String,

    @field:NotBlank
    val password: String
)