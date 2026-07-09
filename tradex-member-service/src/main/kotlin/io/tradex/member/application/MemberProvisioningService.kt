package io.tradex.member.application

import io.tradex.cell.CellFabric
import io.tradex.core.event.AggregateId
import io.tradex.core.query.AggregateRepository
import io.tradex.member.contract.MemberCreated
import io.tradex.member.contract.MemberCreationRevoked
import io.tradex.member.domain.BirthDate
import io.tradex.member.domain.MemberAggregate
import io.tradex.member.domain.Name
import io.tradex.member.domain.PhoneNumber
import io.tradex.member.domain.PhoneNumberAlreadyExistsException
import io.tradex.member.port.PhoneNumberHasher
import io.tradex.member.port.PiiCipher
import io.tradex.member.readmodel.MemberReadModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import org.springframework.stereotype.Service

data class PreparedMember(
    val encryptedName: String,
    val encryptedBirthDate: String,
    val encryptedPhoneNumber: String,
    val phoneNumberHash: String,
)

@Service
class MemberProvisioningService(
    private val fabric: CellFabric,
    private val readModel: MemberReadModel,
    private val piiCipher: PiiCipher,
    private val phoneNumberHasher: PhoneNumberHasher,
    private val clock: Clock,
) {
    fun prepareMember(rawName: String, rawBirthDate: LocalDate, rawPhoneNumber: String): PreparedMember {
        val name = Name.of(rawName)
        val birthDate = BirthDate.of(rawBirthDate, LocalDate.ofInstant(clock.instant(), ZONE))
        val phoneNumber = PhoneNumber.of(rawPhoneNumber)
        val phoneNumberHash = phoneNumberHasher.hash(phoneNumber.e164)

        readModel.catchUp()
        if (readModel.phoneIndex.memberIdOf(phoneNumberHash) != null) throw PhoneNumberAlreadyExistsException()

        return PreparedMember(
            encryptedName = piiCipher.encrypt(name.value),
            encryptedBirthDate = piiCipher.encrypt(birthDate.value.toString()),
            encryptedPhoneNumber = piiCipher.encrypt(phoneNumber.e164),
            phoneNumberHash = phoneNumberHash,
        )
    }

    fun create(memberId: AggregateId, member: PreparedMember) {
        val stream = fabric.readStream(memberId)
        if (stream.any { it.event is MemberCreated }) return

        readModel.catchUp()
        readModel.phoneIndex.memberIdOf(member.phoneNumberHash)?.let { existing ->
            if (existing != memberId) throw PhoneNumberAlreadyExistsException()
        }
        fabric.append(
            "Member", memberId, stream.lastOrNull()?.seqNo ?: 0L,
            listOf(
                MemberCreated(
                    aggregateId = memberId,
                    validTime = clock.instant(),
                    encryptedName = member.encryptedName,
                    encryptedBirthDate = member.encryptedBirthDate,
                    encryptedPhoneNumber = member.encryptedPhoneNumber,
                    phoneNumberHash = member.phoneNumberHash,
                ),
            ),
        )
    }

    fun revoke(memberId: AggregateId, reason: String) {
        val repo = AggregateRepository(MemberAggregate, fabric.cellFor(memberId).store)
        val state = repo.currentState(memberId)
        if (!state.created || state.revoked) return
        fabric.append("Member", memberId, repo.currentSeqNo(memberId), listOf(MemberCreationRevoked(memberId, clock.instant(), reason)))
    }

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
