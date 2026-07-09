package io.tradex.runtime

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "tradex")
data class TradexProperties(

    val storage: Storage = Storage.IN_MEMORY,
    val cellCount: Int = 3,
    val virtualNodes: Int = 128,

    val sessionSecret: String = "tradex-demo-secret",

    val rywTimeoutMs: Long = 2_000,

    val ontologyDir: String = "ontology",

    val tablePrefix: String = "event_store",
) {
    enum class Storage { IN_MEMORY, POSTGRES }
}
