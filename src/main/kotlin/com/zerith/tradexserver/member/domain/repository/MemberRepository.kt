package com.zerith.tradexserver.member.domain.repository

import com.zerith.tradexserver.member.domain.model.Member
import com.zerith.tradexserver.member.domain.model.MemberId
import com.zerith.tradexserver.member.domain.model.PhoneNumberHash

interface MemberRepository {

    fun existsByPhoneNumberHash(hash: PhoneNumberHash): Boolean

    fun findById(id: MemberId): Member?

    fun save(member: Member): Member
}