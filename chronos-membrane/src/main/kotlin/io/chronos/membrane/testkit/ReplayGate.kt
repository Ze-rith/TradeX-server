package io.chronos.membrane.testkit

import io.chronos.core.event.DomainEvent
import io.chronos.membrane.EventSchemaRegistry
import io.chronos.membrane.EventSerde

/**
 * 리플레이 게이트: 레지스트리에 등록된 모든 (type, version 1..latest)에 대해
 * `src/test/resources/<fixtureBasePath>/<type>/v<n>.json` fixture가 존재하고,
 * upcast → 역직렬화 → evolve 적용까지 통과해야 한다.
 *
 * fixture가 없으면 실패로 처리해 **fixture 작성을 강제**한다. 이것이 스키마 진화의
 * 회귀 방지선이다: 새 버전을 만들면 옛 버전 샘플이 계속 리플레이 가능함을 증명해야 빌드가 성공한다.
 */
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
