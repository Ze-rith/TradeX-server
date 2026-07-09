package io.tradex.auth.application

import io.tradex.cell.CellFabric
import io.tradex.core.event.AggregateId
import io.tradex.core.query.AggregateRepository
import io.tradex.auth.contract.AccessTokenBlacklisted
import io.tradex.auth.contract.AuthEvent
import io.tradex.auth.contract.RefreshTokenIssued
import io.tradex.auth.contract.RefreshTokenRevoked
import io.tradex.auth.contract.SignInFailed
import io.tradex.auth.contract.SignInSucceeded
import io.tradex.auth.domain.AccountLockedException
import io.tradex.auth.domain.Email
import io.tradex.auth.domain.InvalidCredentialException
import io.tradex.auth.domain.LockPolicy
import io.tradex.auth.domain.TokenInvalidException
import io.tradex.auth.domain.UserAggregate
import io.tradex.auth.domain.UserState
import io.tradex.auth.port.PasswordHasher
import io.tradex.auth.port.TokenIssuer
import io.tradex.auth.port.TokenPair
import io.tradex.auth.port.TokenSubject
import io.tradex.auth.port.TokenVerifier
import io.tradex.auth.readmodel.AuthReadModel
import java.time.Clock
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val fabric: CellFabric,
    private val readModel: AuthReadModel,
    private val passwordHasher: PasswordHasher,
    private val tokenIssuer: TokenIssuer,
    private val tokenVerifier: TokenVerifier,
    private val lockPolicy: LockPolicy,
    private val clock: Clock,
) {
    private fun repository(userId: AggregateId) =
        AggregateRepository(UserAggregate, fabric.cellFor(userId).store)

    fun signIn(rawEmail: String, rawPassword: String): TokenPair {
        val email = Email.of(rawEmail)
        val now = clock.instant()

        readModel.catchUp()
        val userId = readModel.emailIndex.userIdOf(email.value) ?: throw InvalidCredentialException()
        val repo = repository(userId)
        val user = repo.currentState(userId)
        if (!user.exists) throw InvalidCredentialException()
        if (user.isLocked(now)) throw AccountLockedException()

        val seqNo = repo.currentSeqNo(userId)
        if (!passwordHasher.matches(rawPassword, requireNotNull(user.passwordHash))) {

            val nextCount = user.failureCount + 1
            val lockedUntil = if (nextCount >= lockPolicy.failureThreshold) now.plus(lockPolicy.lockDuration) else user.lockedUntil
            fabric.append("User", userId, seqNo, listOf(SignInFailed(userId, now, nextCount, lockedUntil)))
            throw InvalidCredentialException()
        }

        val tokenPair = tokenIssuer.issue(TokenSubject(userId.toString(), DEFAULT_ROLE))
        val events = buildList {
            add(SignInSucceeded(userId, now))

            user.activeRefreshJti?.let { add(RefreshTokenRevoked(userId, now, it, reason = "superseded by new sign-in")) }
            add(RefreshTokenIssued(userId, now, tokenPair.refreshToken.jti, tokenPair.refreshToken.expiresAt))
        }
        fabric.append("User", userId, seqNo, events)
        return tokenPair
    }

    fun reissue(rawRefreshToken: String): TokenPair {
        val verified = tokenVerifier.verifyRefresh(rawRefreshToken)
        val userId = AggregateId.of(verified.subject.userId)
        val now = clock.instant()
        val repo = repository(userId)
        val user = repo.currentState(userId)
        if (!user.exists) throw TokenInvalidException()

        val seqNo = repo.currentSeqNo(userId)
        if (user.activeRefreshJti != verified.jti) {
            user.activeRefreshJti?.let {
                fabric.append("User", userId, seqNo, listOf(RefreshTokenRevoked(userId, now, it, reason = "refresh reuse detected")))
            }
            throw TokenInvalidException()
        }

        val tokenPair = tokenIssuer.issue(verified.subject)
        fabric.append(
            "User", userId, seqNo,
            listOf(
                RefreshTokenRevoked(userId, now, verified.jti, reason = "rotated"),
                RefreshTokenIssued(userId, now, tokenPair.refreshToken.jti, tokenPair.refreshToken.expiresAt),
            ),
        )
        return tokenPair
    }

    fun signOut(rawAccessToken: String, rawRefreshToken: String) {
        val access = tokenVerifier.verifyAccess(rawAccessToken)
        val refresh = tokenVerifier.verifyRefresh(rawRefreshToken)
        if (access.subject.userId != refresh.subject.userId) throw TokenInvalidException()

        val userId = AggregateId.of(access.subject.userId)
        val now = clock.instant()
        val repo = repository(userId)
        val user = repo.currentState(userId)
        if (!user.exists) throw TokenInvalidException()

        val events = buildList {
            if (user.activeRefreshJti == refresh.jti) {
                add(RefreshTokenRevoked(userId, now, refresh.jti, reason = "sign-out"))
            }
            if (access.expiresAt.isAfter(now)) {
                add(AccessTokenBlacklisted(userId, now, access.jti, access.expiresAt))
            }
        }
        if (events.isNotEmpty()) {
            fabric.append("User", userId, repo.currentSeqNo(userId), events)
        }
    }

    fun validateAccess(rawAccessToken: String): TokenSubject {
        val verified = tokenVerifier.verifyAccess(rawAccessToken)
        val userId = AggregateId.of(verified.subject.userId)
        val user = repository(userId).currentState(userId)
        if (!user.exists || user.isBlacklisted(verified.jti, clock.instant())) throw TokenInvalidException()
        return verified.subject
    }

    fun userState(userId: AggregateId): UserState = repository(userId).currentState(userId)

    companion object {
        private const val DEFAULT_ROLE = "USER"
    }
}
