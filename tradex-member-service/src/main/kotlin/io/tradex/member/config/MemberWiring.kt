package io.tradex.member.config

import io.tradex.cell.CellFabric
import io.tradex.runtime.EventSchemaContributor
import io.tradex.member.contract.MemberCreated
import io.tradex.member.contract.MemberCreationRevoked
import io.tradex.member.infra.AesGcmPiiCipher
import io.tradex.member.infra.HmacPhoneNumberHasher
import io.tradex.member.infra.MemberCryptoProperties
import io.tradex.member.port.PhoneNumberHasher
import io.tradex.member.port.PiiCipher
import io.tradex.member.readmodel.MemberReadModel
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
