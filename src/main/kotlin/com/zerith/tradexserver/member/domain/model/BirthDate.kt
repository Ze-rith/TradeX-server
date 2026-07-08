package com.zerith.tradexserver.member.domain.model

import com.zerith.tradexserver.member.domain.exception.InvalidBirthDateException
import com.zerith.tradexserver.member.domain.exception.UnderageException
import java.time.LocalDate
import java.time.Period

class BirthDate private constructor(
    val value: LocalDate
) {
    companion object {
        private val MIN_DATE: LocalDate = LocalDate.of(1900, 1, 1)
        private const val MIN_AGE = 14

        fun of(
            value: LocalDate,
            today: LocalDate
        ): BirthDate {
            if (value.isBefore(MIN_DATE)) {
                throw InvalidBirthDateException()
            }
            if (!value.isBefore(today)) {
                throw InvalidBirthDateException()
            }
            val age = Period.between(value, today).years
            if (age < MIN_AGE) {
                throw UnderageException()
            }
            return BirthDate(value)
        }

        fun restore(value: LocalDate): BirthDate {
            return BirthDate(value)
        }
    }

    override fun toString(): String {
        return "BirthDate(masked)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BirthDate) return false
        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}