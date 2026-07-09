package io.tradex.registration.application

import io.tradex.cell.CellFabric
import io.tradex.core.event.AggregateId
import io.tradex.saga.SagaDefinition
import io.tradex.saga.engine.JacksonSagaContextCodec
import io.tradex.saga.engine.RealStepExecutor
import io.tradex.saga.engine.SagaOutcome
import io.tradex.registration.port.MemberProvisioningPort
import io.tradex.registration.port.UserProvisioningPort
import io.tradex.registration.saga.RegisterAccountCtx
import java.time.LocalDate
import org.springframework.stereotype.Service

class RegistrationFailedException(val outcome: SagaOutcome) :
    RuntimeException("등록 사가가 완료되지 못했습니다: $outcome")

@Service
class RegistrationService(
    private val fabric: CellFabric,
    private val users: UserProvisioningPort,
    private val members: MemberProvisioningPort,
    private val sagaDefinition: SagaDefinition<RegisterAccountCtx>,
) {
    private val codec = JacksonSagaContextCodec(RegisterAccountCtx::class.java)
    private val executor = RealStepExecutor<RegisterAccountCtx>()

    fun register(email: String, rawPassword: String, name: String, birthDate: LocalDate, phoneNumber: String): AggregateId {
        val credential = users.prepareCredential(email, rawPassword)
        val member = members.prepareMember(name, birthDate, phoneNumber)

        val userId = AggregateId.new()
        val ctx = RegisterAccountCtx(
            userId = userId.toString(),
            email = credential.email,
            passwordHash = credential.passwordHash,
            encryptedName = member.encryptedName,
            encryptedBirthDate = member.encryptedBirthDate,
            encryptedPhoneNumber = member.encryptedPhoneNumber,
            phoneNumberHash = member.phoneNumberHash,
        )

        val sagaId = AggregateId.new()
        val outcome = fabric.cellFor(sagaId).sagaEngine(sagaDefinition, executor, codec).start(sagaId, ctx)
        if (outcome != SagaOutcome.COMPLETED) throw RegistrationFailedException(outcome)
        return userId
    }
}
