package com.zerith.tradexserver.auth.domain.repository

import com.zerith.tradexserver.auth.domain.model.Email
import com.zerith.tradexserver.auth.domain.model.User
import com.zerith.tradexserver.auth.domain.model.UserId

interface UserRepository {

    fun existsByEmail(email: Email): Boolean

    fun findByEmail(email: Email): User?

    fun findById(id: UserId): User?

    fun save(user: User): User
}