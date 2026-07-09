package io.chronos.tradex.registration

import io.chronos.cell.CellFabric
import io.chronos.core.event.AggregateId
import io.chronos.saga.SagaDefinition
import io.chronos.saga.engine.JacksonSagaContextCodec
import io.chronos.saga.engine.RealStepExecutor
import io.chronos.saga.engine.SagaOutcome
import io.chronos.tradex.auth.domain.Email
import io.chronos.tradex.auth.domain.EmailAlreadyExistsException
import io.chronos.tradex.auth.domain.PasswordPolicy
import io.chronos.tradex.auth.port.PasswordHasher
import io.chronos.tradex.member.domain.BirthDate
import io.chronos.tradex.member.domain.Name
import io.chronos.tradex.member.domain.PhoneNumber
import io.chronos.tradex.member.domain.PhoneNumberAlreadyExistsException
import io.chronos.tradex.member.port.PhoneNumberHasher
import io.chronos.tradex.member.port.PiiCipher
import io.chronos.tradex.readmodel.TradexReadModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import org.springframework.stereotype.Service

class RegistrationFailedException(outcome: SagaOutcome) :
    RuntimeException("등록 사가가 완료되지 못했습니다: $outcome")

/**
 * 레거시 RegisterAccountUseCase(단일 @Transactional) → 검증 선행 + 크로스 애그리게잇 사가.
 */
@Service
class RegistrationService(
    private val fabric: CellFabric,
    private val readModel: TradexReadModel,
    private val passwordHasher: PasswordHasher,
    private val piiCipher: PiiCipher,
    private val phoneNumberHasher: PhoneNumberHasher,
    private val sagaDefinition: SagaDefinition<RegisterAccountCtx>,
    private val clock: Clock,
) {
    private val codec = JacksonSagaContextCodec(RegisterAccountCtx::class.java)
    private val executor = RealStepExecutor<RegisterAccountCtx>()

    fun register(rawEmail: String, rawPassword: String, rawName: String, rawBirthDate: LocalDate, rawPhoneNumber: String): AggregateId {
        // 1) 도메인 검증 (레거시 정책 그대로) — 사가 시작 전에 fail fast
        val email = Email.of(rawEmail)
        PasswordPolicy.validate(rawPassword, email)
        val name = Name.of(rawName)
        val birthDate = BirthDate.of(rawBirthDate, LocalDate.ofInstant(clock.instant(), ZONE))
        val phoneNumber = PhoneNumber.of(rawPhoneNumber)
        val phoneNumberHash = phoneNumberHasher.hash(phoneNumber.e164)

        // 2) 유니크 검사 — 인덱스 프로젝션 동기 캐치업 후 (D20)
        readModel.catchUp()
        if (readModel.emailIndex.userIdOf(email.value) != null) throw EmailAlreadyExistsException()
        if (readModel.phoneIndex.memberIdOf(phoneNumberHash) != null) throw PhoneNumberAlreadyExistsException()

        // 3) 사가 실행 — User 등록 → Member 생성, 실패 시 역순 보상
        val userId = AggregateId.new()
        val ctx = RegisterAccountCtx(
            userId = userId.toString(),
            email = email.value,
            passwordHash = passwordHasher.hash(rawPassword),
            encryptedName = piiCipher.encrypt(name.value),
            encryptedBirthDate = piiCipher.encrypt(birthDate.value.toString()),
            encryptedPhoneNumber = piiCipher.encrypt(phoneNumber.e164),
            phoneNumberHash = phoneNumberHash,
        )
        val sagaId = AggregateId.new()
        val outcome = fabric.cellFor(sagaId).sagaEngine(sagaDefinition, executor, codec).start(sagaId, ctx)
        if (outcome != SagaOutcome.COMPLETED) throw RegistrationFailedException(outcome)
        return userId
    }

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
