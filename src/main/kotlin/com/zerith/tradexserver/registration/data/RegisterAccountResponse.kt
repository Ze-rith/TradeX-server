package com.zerith.tradexserver.registration.data

data class RegisterAccountResponse(
    val userId: String
) {
    companion object {
        fun of(userId: String): RegisterAccountResponse {
            return RegisterAccountResponse(userId)
        }
    }
}