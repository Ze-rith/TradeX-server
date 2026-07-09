package io.tradex.auth.application

import io.tradex.cell.CellFabric
import io.tradex.core.event.AggregateId
import io.tradex.core.query.AggregateRepository
import io.tradex.auth.contract.UserRegistered
import io.tradex.auth.contract.UserRegistrationRevoked
import io.tradex.auth.domain.Email
import io.tradex.auth.domain.EmailAlreadyExistsException
import io.tradex.auth.domain.PasswordPolicy
import io.tradex.auth.domain.UserAggregate
import io.tradex.auth.port.PasswordHasher
import io.tradex.auth.readmodel.AuthReadModel
import java.time.Clock
import org.springframework.stereotype.Service

data class PreparedCredential(val email: String, val passwordHash: String)

@Service
class UserProvisioningService(
    private val fabric: CellFabric,
    private val readModel: AuthReadModel,
    private val passwordHasher: PasswordHasher,
    private val clock: Clock,
) {

    fun prepareCredential(rawEmail: String, rawPassword: String): PreparedCredential {
        val email = Email.of(rawEmail)
        PasswordPolicy.validate(rawPassword, email)
        readModel.catchUp()
        if (readModel.emailIndex.userIdOf(email.value) != null) throw EmailAlreadyExistsException()
        return PreparedCredential(email.value, passwordHasher.hash(rawPassword))
    }

    fun register(userId: AggregateId, email: String, passwordHash: String) {
        val stream = fabric.readStream(userId)
        if (stream.any { it.event is UserRegistered }) return

        readModel.catchUp()
        readModel.emailIndex.userIdOf(email)?.let { existing ->
            if (existing != userId) throw EmailAlreadyExistsException()
        }
        fabric.append("User", userId, stream.lastOrNull()?.seqNo ?: 0L, listOf(UserRegistered(userId, clock.instant(), email, passwordHash)))
    }

    fun revoke(userId: AggregateId, reason: String) {
        val repo = AggregateRepository(UserAggregate, fabric.cellFor(userId).store)
        val state = repo.currentState(userId)
        if (!state.registered || state.revoked) return
        fabric.append("User", userId, repo.currentSeqNo(userId), listOf(UserRegistrationRevoked(userId, clock.instant(), reason)))
    }
}
