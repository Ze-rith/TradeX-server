package io.tradex.membrane

import com.fasterxml.jackson.databind.ObjectMapper
import io.tradex.core.event.DomainEvent

data class SerializedEvent(val type: String, val version: Int, val json: String)

/**
 * 진화막의 핵심: 역직렬화는 **항상 최신 버전으로 승격한 뒤에만** 코어에 전달한다.
 * 코어(evolve)는 과거 스키마의 존재를 모른다.
 */
class EventSerde(
    private val registry: EventSchemaRegistry,
    private val upcasters: UpcasterChain,
    val objectMapper: ObjectMapper = TradexJson.mapper(),
) {
    fun serialize(event: DomainEvent): SerializedEvent {
        val schema = registry.schemaFor(event::class)
        return SerializedEvent(schema.type, schema.latestVersion, objectMapper.writeValueAsString(event))
    }

    fun deserialize(type: String, storedVersion: Int, payloadJson: String): DomainEvent {
        val schema = registry.schemaFor(type)
        val upcasted = upcasters.upcast(type, storedVersion, schema.latestVersion, objectMapper.readTree(payloadJson))
        return objectMapper.treeToValue(upcasted, schema.eventClass.java)
    }

    fun typeOf(event: DomainEvent): RegisteredSchema = registry.schemaFor(event::class)
}
