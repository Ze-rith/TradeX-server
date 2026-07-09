package io.tradex.membrane

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import io.tradex.core.event.AggregateId
import io.tradex.core.event.EventId

object TradexJson {
    fun mapper(): ObjectMapper = jacksonMapperBuilder()
        .addModule(JavaTimeModule())
        .addModule(identifierModule())
        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    private fun identifierModule() = SimpleModule("tradex-identifiers").apply {
        addSerializer(EventId::class.java, object : StdSerializer<EventId>(EventId::class.java) {
            override fun serialize(value: EventId, gen: JsonGenerator, provider: SerializerProvider) =
                gen.writeString(value.toString())
        })
        addDeserializer(EventId::class.java, object : StdDeserializer<EventId>(EventId::class.java) {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext) = EventId.of(p.valueAsString)
        })
        addSerializer(AggregateId::class.java, object : StdSerializer<AggregateId>(AggregateId::class.java) {
            override fun serialize(value: AggregateId, gen: JsonGenerator, provider: SerializerProvider) =
                gen.writeString(value.toString())
        })
        addDeserializer(AggregateId::class.java, object : StdDeserializer<AggregateId>(AggregateId::class.java) {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext) = AggregateId.of(p.valueAsString)
        })
    }
}
