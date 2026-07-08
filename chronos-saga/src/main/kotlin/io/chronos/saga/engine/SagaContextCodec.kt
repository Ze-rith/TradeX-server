package io.chronos.saga.engine

import com.fasterxml.jackson.databind.ObjectMapper
import io.chronos.membrane.ChronosJson

/** SagaStarted에 실리는 컨텍스트 직렬화. 컨텍스트는 순수 데이터여야 한다 (포트 참조 금지). */
interface SagaContextCodec<C> {
    fun encode(context: C): String
    fun decode(json: String): C
}

class JacksonSagaContextCodec<C : Any>(
    private val contextClass: Class<C>,
    private val objectMapper: ObjectMapper = ChronosJson.mapper(),
) : SagaContextCodec<C> {
    override fun encode(context: C): String = objectMapper.writeValueAsString(context)
    override fun decode(json: String): C = objectMapper.readValue(json, contextClass)
}
