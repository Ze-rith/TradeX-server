package io.chronos.tradex.registration.config

import com.fasterxml.jackson.databind.ObjectMapper
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
import io.chronos.tradex.registration.infra.HttpMemberProvisioningAdapter
import io.chronos.tradex.registration.infra.HttpUserProvisioningAdapter
import io.chronos.tradex.registration.infra.ServiceEndpoints
import io.chronos.tradex.registration.port.MemberProvisioningPort
import io.chronos.tradex.registration.port.UserProvisioningPort
import io.chronos.tradex.registration.saga.RegisterAccountCtx
import io.chronos.tradex.registration.saga.registerAccountSaga
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ServiceEndpoints::class)
class RegistrationWiring {
    /** 이 서비스의 스토어에는 사가 엔진 이벤트만 기록된다. */
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
