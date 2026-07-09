package io.tradex.auth.config

import io.tradex.cell.CellFabric
import io.tradex.runtime.EventSchemaContributor
import io.tradex.auth.contract.AccessTokenBlacklisted
import io.tradex.auth.contract.RefreshTokenIssued
import io.tradex.auth.contract.RefreshTokenRevoked
import io.tradex.auth.contract.SignInFailed
import io.tradex.auth.contract.SignInSucceeded
import io.tradex.auth.contract.UserRegistered
import io.tradex.auth.contract.UserRegistrationRevoked
import io.tradex.auth.domain.LockPolicy
import io.tradex.auth.infra.BcryptPasswordHasher
import io.tradex.auth.infra.JwtProperties
import io.tradex.auth.infra.JwtTokenAdapter
import io.tradex.auth.port.PasswordHasher
import io.tradex.auth.readmodel.AuthReadModel
import java.time.Clock
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(JwtProperties::class)
class AuthWiring {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun authEventSchemas(): EventSchemaContributor = EventSchemaContributor { registry ->
        registry.register(UserRegistered::class)
        registry.register(UserRegistrationRevoked::class)
        registry.register(SignInFailed::class)
        registry.register(SignInSucceeded::class)
        registry.register(RefreshTokenIssued::class)
        registry.register(RefreshTokenRevoked::class)
        registry.register(AccessTokenBlacklisted::class)
    }

    @Bean
    fun passwordHasher(): PasswordHasher = BcryptPasswordHasher()

    @Bean
    fun jwtTokenAdapter(props: JwtProperties, clock: Clock): JwtTokenAdapter = JwtTokenAdapter(props, clock)

    @Bean
    fun lockPolicy(): LockPolicy = LockPolicy()

    @Bean
    fun authReadModel(fabric: CellFabric): AuthReadModel = AuthReadModel(fabric)
}
