package com.zerith.tradexserver.auth.data

data class RegisterUserResponse(
    val userId: String
) {
    companion object {
        fun of(userId: String): RegisterUserResponse {
            return RegisterUserResponse(userId)
        }
    }
}