package io.tradex.saga.engine

import com.fasterxml.jackson.databind.ObjectMapper
import io.tradex.membrane.TradexJson

interface SagaContextCodec<C> {
    fun encode(context: C): String
    fun decode(json: String): C
}

class JacksonSagaContextCodec<C : Any>(
    private val contextClass: Class<C>,
    private val objectMapper: ObjectMapper = TradexJson.mapper(),
) : SagaContextCodec<C> {
    override fun encode(context: C): String = objectMapper.writeValueAsString(context)
    override fun decode(json: String): C = objectMapper.readValue(json, contextClass)
}
