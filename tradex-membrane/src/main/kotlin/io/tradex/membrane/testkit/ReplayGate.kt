package io.tradex.membrane.testkit

import io.tradex.core.event.DomainEvent
import io.tradex.membrane.EventSchemaRegistry
import io.tradex.membrane.EventSerde

class ReplayGate(
    private val registry: EventSchemaRegistry,
    private val serde: EventSerde,
    private val applyEvolve: (DomainEvent) -> Unit,
    private val fixtureBasePath: String = "fixtures",
    private val classLoader: ClassLoader = Thread.currentThread().contextClassLoader,
) {
    fun failures(): List<String> = registry.all().flatMap { schema ->
        (1..schema.latestVersion).mapNotNull { version ->
            val resource = "$fixtureBasePath/${schema.type}/v$version.json"
            val stream = classLoader.getResourceAsStream(resource)
                ?: return@mapNotNull "fixture 누락: $resource — @EventSchema(type=\"${schema.type}\", version=${schema.latestVersion})의 v$version 샘플을 작성하라"
            runCatching {
                val event = serde.deserialize(schema.type, version, stream.bufferedReader().readText())
                applyEvolve(event)
            }.exceptionOrNull()?.let { "$resource 리플레이 실패: $it" }
        }
    }

    fun verify() {
        val failures = failures()
        if (failures.isNotEmpty()) {
            throw AssertionError("리플레이 게이트 실패 (${failures.size}건):\n${failures.joinToString("\n")}")
        }
    }
}
