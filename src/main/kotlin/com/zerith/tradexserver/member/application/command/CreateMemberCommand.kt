package com.zerith.tradexserver.member.application.command

import java.time.LocalDate
import java.util.UUID

class CreateMemberCommand private constructor(
    val memberId: UUID,
    val name: String,
    val birthDate: LocalDate,
    val phoneNumber: String
) {
    companion object {
        fun of(
            memberId: UUID,
            name: String,
            birthDate: LocalDate,
            phoneNumber: String
        ): CreateMemberCommand {
            return CreateMemberCommand(memberId, name, birthDate, phoneNumber)
        }
    }
}