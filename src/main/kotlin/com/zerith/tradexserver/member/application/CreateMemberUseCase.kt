package com.zerith.tradexserver.member.application

import com.zerith.tradexserver.member.application.command.CreateMemberCommand
import com.zerith.tradexserver.member.domain.exception.PhoneNumberAlreadyExistsException
import com.zerith.tradexserver.member.domain.model.BirthDate
import com.zerith.tradexserver.member.domain.model.Member
import com.zerith.tradexserver.member.domain.model.MemberId
import com.zerith.tradexserver.member.domain.model.Name
import com.zerith.tradexserver.member.domain.model.PhoneNumber
import com.zerith.tradexserver.member.domain.port.PhoneNumberHasher
import com.zerith.tradexserver.member.domain.repository.MemberRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@Component
class CreateMemberUseCase(
    private val memberRepository: MemberRepository,
    private val phoneNumberHasher: PhoneNumberHasher,
    private val clock: Clock
) {

    @Transactional
    fun execute(command: CreateMemberCommand): MemberId {
        val name = Name.of(command.name)

        val today = LocalDate.ofInstant(clock.instant(), ZONE)
        val birthDate = BirthDate.of(command.birthDate, today)

        val phoneNumber = PhoneNumber.of(command.phoneNumber)
        val phoneNumberHash = phoneNumberHasher.hash(phoneNumber)

        if (memberRepository.existsByPhoneNumberHash(phoneNumberHash)) {
            throw PhoneNumberAlreadyExistsException()
        }

        val now = clock.instant()
        val member = Member.create(
            id = MemberId.from(command.memberId),
            name = name,
            birthDate = birthDate,
            phoneNumber = phoneNumber,
            phoneNumberHash = phoneNumberHash,
            now = now
        )

        val saved = memberRepository.save(member)
        return saved.id
    }

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}