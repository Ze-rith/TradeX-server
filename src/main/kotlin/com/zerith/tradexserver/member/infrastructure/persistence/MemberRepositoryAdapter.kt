package com.zerith.tradexserver.member.infrastructure.persistence

import com.zerith.tradexserver.member.domain.model.Member
import com.zerith.tradexserver.member.domain.model.MemberId
import com.zerith.tradexserver.member.domain.model.PhoneNumberHash
import com.zerith.tradexserver.member.domain.repository.MemberRepository
import org.springframework.stereotype.Component

@Component
class MemberRepositoryAdapter(
    private val memberJpaRepository: MemberJpaRepository,
    private val memberMapper: MemberMapper
) : MemberRepository {

    override fun existsByPhoneNumberHash(hash: PhoneNumberHash): Boolean {
        return memberJpaRepository.existsByPhoneNumberHash(hash.value)
    }

    override fun findById(id: MemberId): Member? {
        val entity = memberJpaRepository.findById(id.value).orElse(null) ?: return null
        return memberMapper.toDomain(entity)
    }

    override fun save(member: Member): Member {
        val existing = memberJpaRepository.findById(member.id.value).orElse(null)
        val saved = if (existing == null) {
            memberJpaRepository.save(memberMapper.toEntity(member))
        } else {
            memberMapper.applyTo(existing, member)
            memberJpaRepository.save(existing)
        }
        return memberMapper.toDomain(saved)
    }
}