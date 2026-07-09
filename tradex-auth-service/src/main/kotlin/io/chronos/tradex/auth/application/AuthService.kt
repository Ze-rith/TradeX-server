package io.chronos.tradex.auth.application

import io.chronos.cell.CellFabric
import io.chronos.core.event.AggregateId
import io.chronos.core.query.AggregateRepository
import io.chronos.tradex.auth.contract.AccessTokenBlacklisted
import io.chronos.tradex.auth.contract.AuthEvent
import io.chronos.tradex.auth.contract.RefreshTokenIssued
import io.chronos.tradex.auth.contract.RefreshTokenRevoked
import io.chronos.tradex.auth.contract.SignInFailed
import io.chronos.tradex.auth.contract.SignInSucceeded
import io.chronos.tradex.auth.domain.AccountLockedException
import io.chronos.tradex.auth.domain.Email
import io.chronos.tradex.auth.domain.InvalidCredentialException
import io.chronos.tradex.auth.domain.LockPolicy
import io.chronos.tradex.auth.domain.TokenInvalidException
import io.chronos.tradex.auth.domain.UserAggregate
import io.chronos.tradex.auth.domain.UserState
import io.chronos.tradex.auth.port.PasswordHasher
import io.chronos.tradex.auth.port.TokenIssuer
import io.chronos.tradex.auth.port.TokenPair
import io.chronos.tradex.auth.port.TokenSubject
import io.chronos.tradex.auth.port.TokenVerifier
import io.chronos.tradex.auth.readmodel.AuthReadModel
import java.time.Clock
import org.springframework.stereotype.Service

/**
 * 레거시 SignIn/Reissue/SignOut/Validate UseCase의 이벤트소싱 재구현.
 * 모든 상태 변경은 User 스트림 append, 모든 판단은 리플레이된 [UserState]에서.
 */
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
            // 잠금 여부를 여기서 결정해 이벤트에 기록한다 (D19)
            val nextCount = user.failureCount + 1
            val lockedUntil = if (nextCount >= lockPolicy.failureThreshold) now.plus(lockPolicy.lockDuration) else user.lockedUntil
            fabric.append("User", userId, seqNo, listOf(SignInFailed(userId, now, nextCount, lockedUntil)))
            throw InvalidCredentialException()
        }

        val tokenPair = tokenIssuer.issue(TokenSubject(userId.toString(), DEFAULT_ROLE))
        val events = buildList {
            add(SignInSucceeded(userId, now))
            // 단일 활성 세션: 기존 refresh가 있으면 회전 폐기
            user.activeRefreshJti?.let { add(RefreshTokenRevoked(userId, now, it, reason = "superseded by new sign-in")) }
            add(RefreshTokenIssued(userId, now, tokenPair.refreshToken.jti, tokenPair.refreshToken.expiresAt))
        }
        fabric.append("User", userId, seqNo, events)
        return tokenPair
    }

    /** refresh 회전: 활성 jti 일치 검사 → Revoked+Issued 원자 배치. 불일치는 재사용 신호 → 방어적 전면 폐기. */
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

    /** 레거시 ValidateAccessTokenUseCase: 서명·만료 검증 + 블랙리스트 확인. */
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
