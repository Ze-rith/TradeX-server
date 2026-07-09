package io.chronos.tradex.member.domain

import io.chronos.core.aggregate.Aggregate
import io.chronos.tradex.member.contract.MemberCreated
import io.chronos.tradex.member.contract.MemberCreationRevoked
import io.chronos.tradex.member.contract.MemberEvent

/** 상태에는 암호문과 해시만 있다 — 복호화는 조회 계층이 PiiCipher 포트로 수행. */
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
