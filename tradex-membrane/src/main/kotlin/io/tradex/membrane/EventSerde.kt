package io.tradex.membrane

import com.fasterxml.jackson.databind.ObjectMapper
import io.tradex.core.event.DomainEvent

data class SerializedEvent(val type: String, val version: Int, val json: String)

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
