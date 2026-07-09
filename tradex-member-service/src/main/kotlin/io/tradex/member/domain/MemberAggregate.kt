package io.tradex.member.domain

import io.tradex.core.aggregate.Aggregate
import io.tradex.member.contract.MemberCreated
import io.tradex.member.contract.MemberCreationRevoked
import io.tradex.member.contract.MemberEvent

data class MemberState(
    val created: Boolean = false,
    val revoked: Boolean = false,
    val encryptedName: String? = null,
    val encryptedBirthDate: String? = null,
    val encryptedPhoneNumber: String? = null,
    val phoneNumberHash: String? = null,
) {
    val exists: Boolean get() = created && !revoked
}

object MemberAggregate : Aggregate<MemberState, MemberEvent> {
    override val type = "Member"
    override val initial = MemberState()

    override fun evolve(state: MemberState, event: MemberEvent): MemberState = when (event) {
        is MemberCreated -> state.copy(
            created = true,
            encryptedName = event.encryptedName,
            encryptedBirthDate = event.encryptedBirthDate,
            encryptedPhoneNumber = event.encryptedPhoneNumber,
            phoneNumberHash = event.phoneNumberHash,
        )
        is MemberCreationRevoked -> state.copy(revoked = true)
    }
}
