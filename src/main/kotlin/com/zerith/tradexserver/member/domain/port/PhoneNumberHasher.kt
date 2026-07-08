package com.zerith.tradexserver.member.domain.port

import com.zerith.tradexserver.member.domain.model.PhoneNumber
import com.zerith.tradexserver.member.domain.model.PhoneNumberHash

interface PhoneNumberHasher {

    fun hash(phoneNumber: PhoneNumber): PhoneNumberHash
}