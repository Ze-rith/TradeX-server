package com.zerith.tradexserver.auth.domain.policy

import com.zerith.tradexserver.auth.domain.exception.WeakPasswordException
import com.zerith.tradexserver.auth.domain.model.Email

class PasswordPolicy {

    fun validate(
        rawPassword: String,
        email: Email
    ) {
        if (rawPassword.length < MIN_LENGTH) {
            throw WeakPasswordException()
        }
        val categories = countCategories(rawPassword)
        if (categories < REQUIRED_CATEGORIES) {
            throw WeakPasswordException()
        }
        if (rawPassword.lowercase().contains(email.localPart().lowercase())) {
            throw WeakPasswordException()
        }
    }

    private fun countCategories(raw: String): Int {
        val hasLower = raw.any {
            it.isLowerCase()
        }
        val hasUpper = raw.any {
            it.isUpperCase()
        }
        val hasDigit = raw.any {
            it.isDigit()
        }
        val hasSpecial = raw.any {
            !it.isLetterOrDigit()
        }
        return listOf(hasLower, hasUpper, hasDigit, hasSpecial).count {
            it
        }
    }

    companion object {
        private const val MIN_LENGTH = 12
        private const val REQUIRED_CATEGORIES = 3
    }
}