package io.chronos.tradex.config

import io.chronos.cell.CellFabric
import io.chronos.runtime.EventSchemaContributor
import io.chronos.saga.SagaDefinition
import io.chronos.saga.engine.CompensationFailed
import io.chronos.saga.engine.CompensationStarted
import io.chronos.saga.engine.CompensationSucceeded
import io.chronos.saga.engine.SagaCompensated
import io.chronos.saga.engine.SagaCompleted
import io.chronos.saga.engine.SagaStarted
import io.chronos.saga.engine.StepFailed
import io.chronos.saga.engine.StepSucceeded
import io.chronos.saga.engine.StepTimedOut
import io.chronos.tradex.auth.contract.AccessTokenBlacklisted
import io.chronos.tradex.auth.contract.RefreshTokenIssued
import io.chronos.tradex.auth.contract.RefreshTokenRevoked
import io.chronos.tradex.auth.contract.SignInFailed
import io.chronos.tradex.auth.contract.SignInSucceeded
import io.chronos.tradex.auth.contract.UserRegistered
import io.chronos.tradex.auth.contract.UserRegistrationRevoked
import io.chronos.tradex.auth.domain.LockPolicy
import io.chronos.tradex.auth.infra.BcryptPasswordHasher
import io.chronos.tradex.auth.infra.JwtProperties
import io.chronos.tradex.auth.infra.JwtTokenAdapter
import io.chronos.tradex.auth.port.PasswordHasher
import io.chronos.tradex.member.contract.MemberCreated
import io.chronos.tradex.member.contract.MemberCreationRevoked
import io.chronos.tradex.member.infra.AesGcmPiiCipher
import io.chronos.tradex.member.infra.HmacPhoneNumberHasher
import io.chronos.tradex.member.infra.MemberCryptoProperties
import io.chronos.tradex.member.port.PhoneNumberHasher
import io.chronos.tradex.member.port.PiiCipher
import io.chronos.tradex.readmodel.TradexReadModel
import io.chronos.tradex.registration.RegisterAccountCtx
import io.chronos.tradex.registration.registerAccountSaga
import java.time.Clock
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(JwtProperties::class, MemberCryptoProperties::class)
class TradexWiring {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    /** 이 앱이 저장하는 모든 이벤트 — 온톨로지 가드·serde·리플레이 게이트의 재료. */
    @Bean
    fun tradexEventSchemas(): EventSchemaContributor = EventSchemaContributor { registry ->
        registry.register(UserRegistered::class)
        registry.register(UserRegistrationRevoked::class)
        registry.register(SignInFailed::class)
        registry.register(SignInSucceeded::class)
        registry.register(RefreshTokenIssued::class)
        registry.register(RefreshTokenRevoked::class)
        registry.register(AccessTokenBlacklisted::class)
        registry.register(MemberCreated::class)
        registry.register(MemberCreationRevoked::class)

        // 사가 엔진 이벤트도 같은 스토어에 기록된다
        registry.register(SagaStarted::class)
        registry.register(StepSucceeded::class)
        registry.register(StepFailed::class)
        registry.register(StepTimedOut::class)
        registry.register(CompensationStarted::class)
        registry.register(CompensationSucceeded::class)
        registry.register(CompensationFailed::class)
        registry.register(SagaCompleted::class)
        registry.register(SagaCompensated::class)
    }

    @Bean
    fun passwordHasher(): PasswordHasher = BcryptPasswordHasher()

    @Bean
    fun jwtTokenAdapter(props: JwtProperties, clock: Clock): JwtTokenAdapter = JwtTokenAdapter(props, clock)

    @Bean
    fun piiCipher(props: MemberCryptoProperties): PiiCipher = AesGcmPiiCipher(props)

    @Bean
    fun phoneNumberHasher(props: MemberCryptoProperties): PhoneNumberHasher = HmacPhoneNumberHasher(props)

    @Bean
    fun lockPolicy(): LockPolicy = LockPolicy()

    @Bean
    fun tradexReadModel(fabric: CellFabric): TradexReadModel = TradexReadModel(fabric)

    @Bean
    fun registerAccountSagaDefinition(fabric: CellFabric, clock: Clock): SagaDefinition<RegisterAccountCtx> =
        registerAccountSaga(fabric, clock)
}
