package io.chronos.tradex.member.config

import io.chronos.cell.CellFabric
import io.chronos.runtime.EventSchemaContributor
import io.chronos.tradex.member.contract.MemberCreated
import io.chronos.tradex.member.contract.MemberCreationRevoked
import io.chronos.tradex.member.infra.AesGcmPiiCipher
import io.chronos.tradex.member.infra.HmacPhoneNumberHasher
import io.chronos.tradex.member.infra.MemberCryptoProperties
import io.chronos.tradex.member.port.PhoneNumberHasher
import io.chronos.tradex.member.port.PiiCipher
import io.chronos.tradex.member.readmodel.MemberReadModel
import java.time.Clock
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(MemberCryptoProperties::class)
class MemberWiring {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun memberEventSchemas(): EventSchemaContributor = EventSchemaContributor { registry ->
        registry.register(MemberCreated::class)
        registry.register(MemberCreationRevoked::class)
    }

    @Bean
    fun piiCipher(props: MemberCryptoProperties): PiiCipher = AesGcmPiiCipher(props)

    @Bean
    fun phoneNumberHasher(props: MemberCryptoProperties): PhoneNumberHasher = HmacPhoneNumberHasher(props)

    @Bean
    fun memberReadModel(fabric: CellFabric): MemberReadModel = MemberReadModel(fabric)
}
