package com.zerith.tradexserver.auth.application.command

class ReissueTokenCommand private constructor(
    val rawRefreshToken: String
) {
    companion object {
        fun of(rawRefreshToken: String): ReissueTokenCommand {
            return ReissueTokenCommand(rawRefreshToken)
        }
    }
}