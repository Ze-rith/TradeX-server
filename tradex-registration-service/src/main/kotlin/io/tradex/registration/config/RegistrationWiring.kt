package io.tradex.registration.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.tradex.runtime.EventSchemaContributor
import io.tradex.saga.SagaDefinition
import io.tradex.saga.engine.CompensationFailed
import io.tradex.saga.engine.CompensationStarted
import io.tradex.saga.engine.CompensationSucceeded
import io.tradex.saga.engine.SagaCompensated
import io.tradex.saga.engine.SagaCompleted
import io.tradex.saga.engine.SagaStarted
import io.tradex.saga.engine.StepFailed
import io.tradex.saga.engine.StepSucceeded
import io.tradex.saga.engine.StepTimedOut
import io.tradex.registration.infra.HttpMemberProvisioningAdapter
import io.tradex.registration.infra.HttpUserProvisioningAdapter
import io.tradex.registration.infra.ServiceEndpoints
import io.tradex.registration.port.MemberProvisioningPort
import io.tradex.registration.port.UserProvisioningPort
import io.tradex.registration.saga.RegisterAccountCtx
import io.tradex.registration.saga.registerAccountSaga
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ServiceEndpoints::class)
class RegistrationWiring {

    @Bean
    fun sagaEventSchemas(): EventSchemaContributor = EventSchemaContributor { registry ->
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
    fun userProvisioningPort(endpoints: ServiceEndpoints, objectMapper: ObjectMapper): UserProvisioningPort =
        HttpUserProvisioningAdapter(endpoints, objectMapper)

    @Bean
    fun memberProvisioningPort(endpoints: ServiceEndpoints, objectMapper: ObjectMapper): MemberProvisioningPort =
        HttpMemberProvisioningAdapter(endpoints, objectMapper)

    @Bean
    fun registerAccountSagaDefinition(
        users: UserProvisioningPort,
        members: MemberProvisioningPort,
    ): SagaDefinition<RegisterAccountCtx> = registerAccountSaga(users, members)
}
