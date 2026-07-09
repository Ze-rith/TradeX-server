package io.tradex.registration.infra

import com.fasterxml.jackson.databind.ObjectMapper
import io.tradex.registration.port.MemberProvisioningPort
import io.tradex.registration.port.PreparedCredential
import io.tradex.registration.port.PreparedMember
import io.tradex.registration.port.ProvisioningRejectedException
import io.tradex.registration.port.UserProvisioningPort
import java.time.LocalDate
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@ConfigurationProperties(prefix = "tradex.services")
data class ServiceEndpoints(
    val authBaseUrl: String = "http://localhost:8081",
    val memberBaseUrl: String = "http://localhost:8082",
)

private fun <R> mapRejection(objectMapper: ObjectMapper, block: () -> R): R = try {
    block()
} catch (e: RestClientResponseException) {
    if (e.statusCode.is4xxClientError) {
        val body = runCatching { objectMapper.readTree(e.responseBodyAsString) }.getOrNull()
        throw ProvisioningRejectedException(
            status = e.statusCode.value(),
            code = body?.get("code")?.asText() ?: "REJECTED",
            message = body?.get("message")?.asText() ?: e.message ?: "rejected",
        )
    }
    throw e
}

class HttpUserProvisioningAdapter(
    endpoints: ServiceEndpoints,
    private val objectMapper: ObjectMapper,
    builder: RestClient.Builder = RestClient.builder(),
) : UserProvisioningPort {
    private val client = builder.baseUrl(endpoints.authBaseUrl).build()

    override fun prepareCredential(email: String, rawPassword: String): PreparedCredential =
        mapRejection(objectMapper) {
            val response = client.post().uri("/internal/credentials")
                .body(mapOf("email" to email, "password" to rawPassword))
                .retrieve()
                .body(Map::class.java)!!

            @Suppress("UNCHECKED_CAST")
            val data = response["data"] as Map<String, String>
            PreparedCredential(data.getValue("email"), data.getValue("passwordHash"))
        }

    override fun registerUser(userId: String, email: String, passwordHash: String) {
        mapRejection(objectMapper) {
            client.put().uri("/internal/users/{id}", userId)
                .body(mapOf("email" to email, "passwordHash" to passwordHash))
                .retrieve()
                .toBodilessEntity()
        }
    }

    override fun revokeUser(userId: String, reason: String) {
        mapRejection(objectMapper) {
            client.delete().uri("/internal/users/{id}?reason={reason}", userId, reason)
                .retrieve()
                .toBodilessEntity()
        }
    }
}

class HttpMemberProvisioningAdapter(
    endpoints: ServiceEndpoints,
    private val objectMapper: ObjectMapper,
    builder: RestClient.Builder = RestClient.builder(),
) : MemberProvisioningPort {
    private val client = builder.baseUrl(endpoints.memberBaseUrl).build()

    override fun prepareMember(name: String, birthDate: LocalDate, phoneNumber: String): PreparedMember =
        mapRejection(objectMapper) {
            val response = client.post().uri("/internal/members/prepare")
                .body(mapOf("name" to name, "birthDate" to birthDate.toString(), "phoneNumber" to phoneNumber))
                .retrieve()
                .body(Map::class.java)!!

            @Suppress("UNCHECKED_CAST")
            val data = response["data"] as Map<String, String>
            PreparedMember(
                encryptedName = data.getValue("encryptedName"),
                encryptedBirthDate = data.getValue("encryptedBirthDate"),
                encryptedPhoneNumber = data.getValue("encryptedPhoneNumber"),
                phoneNumberHash = data.getValue("phoneNumberHash"),
            )
        }

    override fun createMember(memberId: String, member: PreparedMember) {
        mapRejection(objectMapper) {
            client.put().uri("/internal/members/{id}", memberId)
                .body(
                    mapOf(
                        "encryptedName" to member.encryptedName,
                        "encryptedBirthDate" to member.encryptedBirthDate,
                        "encryptedPhoneNumber" to member.encryptedPhoneNumber,
                        "phoneNumberHash" to member.phoneNumberHash,
                    ),
                )
                .retrieve()
                .toBodilessEntity()
        }
    }

    override fun revokeMember(memberId: String, reason: String) {
        mapRejection(objectMapper) {
            client.delete().uri("/internal/members/{id}?reason={reason}", memberId, reason)
                .retrieve()
                .toBodilessEntity()
        }
    }
}
