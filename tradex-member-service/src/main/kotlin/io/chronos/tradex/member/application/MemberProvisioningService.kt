package io.chronos.tradex.member.application

import io.chronos.cell.CellFabric
import io.chronos.core.event.AggregateId
import io.chronos.core.query.AggregateRepository
import io.chronos.tradex.member.contract.MemberCreated
import io.chronos.tradex.member.contract.MemberCreationRevoked
import io.chronos.tradex.member.domain.BirthDate
import io.chronos.tradex.member.domain.MemberAggregate
import io.chronos.tradex.member.domain.Name
import io.chronos.tradex.member.domain.PhoneNumber
import io.chronos.tradex.member.domain.PhoneNumberAlreadyExistsException
import io.chronos.tradex.member.port.PhoneNumberHasher
import io.chronos.tradex.member.port.PiiCipher
import io.chronos.tradex.member.readmodel.MemberReadModel
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

/**
 * registration-service가 사가 step에서 호출하는 내부 프로비저닝 유스케이스.
 * PII 암호화 키는 이 서비스가 소유한다 — 평문 PII는 프리페어 요청 안에서만 존재하고,
 * 사가 컨텍스트·이벤트에는 암호문만 실린다 (DECISIONS.md D21).
 */
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
        if (stream.any { it.event is MemberCreated }) return // 사가 재시도 멱등

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
        if (!state.created || state.revoked) return // 보상 재시도 멱등
        fabric.append("Member", memberId, repo.currentSeqNo(memberId), listOf(MemberCreationRevoked(memberId, clock.instant(), reason)))
    }

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
