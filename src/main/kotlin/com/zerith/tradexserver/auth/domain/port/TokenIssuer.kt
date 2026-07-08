package com.zerith.tradexserver.auth.domain.port

import com.zerith.tradexserver.auth.domain.token.TokenPair
import com.zerith.tradexserver.auth.domain.token.TokenSubject

interface TokenIssuer {

    fun issue(subject: TokenSubject): TokenPair
}