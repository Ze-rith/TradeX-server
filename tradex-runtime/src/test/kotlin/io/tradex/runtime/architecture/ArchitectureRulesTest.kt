package io.tradex.runtime.architecture

import com.lemonappdev.konsist.api.Konsist
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Test

/**
 * 명세 §2의 의존 방향 규칙을 소스 레벨에서 강제한다.
 * 위반 시 "파일 → 금지된 import" 목록이 실패 메시지로 출력된다.
 */
class ArchitectureRulesTest {
    private val files = Konsist.scopeFromProject().files

    private fun mainFilesOf(module: String) =
        files.filter { it.path.replace('\\', '/').contains("/$module/src/main/") }

    private fun violations(module: String, isAllowed: (String) -> Boolean): List<String> =
        mainFilesOf(module).flatMap { file ->
            file.imports.map { it.name }
                .filterNot(isAllowed)
                .map { "${file.path} -> $it" }
        }

    @Test
    fun `core는 순수 Kotlin이다 - Spring, Jackson 등 어떤 서드파티 import도 금지`() {
        val allowed = listOf("java.", "kotlin.", "io.tradex.core.")
        val bad = violations("tradex-core") { imp -> allowed.any { imp.startsWith(it) } }
        withClue("tradex-core에 허용되지 않은 import:\n${bad.joinToString("\n")}") { bad.shouldBeEmpty() }
    }

    @Test
    fun `바깥 레이어는 안쪽 레이어만 의존한다`() {
        val allowedTradexImports = mapOf(
            "tradex-membrane" to listOf("io.tradex.core.", "io.tradex.membrane."),
            "tradex-saga" to listOf("io.tradex.core.", "io.tradex.membrane.", "io.tradex.saga."),
            "tradex-router" to listOf("io.tradex.core.", "io.tradex.router."),
            "tradex-ontology" to listOf("io.tradex.core.", "io.tradex.membrane.", "io.tradex.ontology."),
            // tradex-cell, tradex-runtime은 전 레이어 의존 허용이므로 규칙 없음
        )

        val bad = allowedTradexImports.flatMap { (module, allowed) ->
            violations(module) { imp -> !imp.startsWith("io.tradex.") || allowed.any { imp.startsWith(it) } }
        }
        withClue("레이어 의존 방향 위반:\n${bad.joinToString("\n")}") { bad.shouldBeEmpty() }
    }

    @Test
    fun `앱 도메인 패키지는 tradex 레이어 중 core 추상화만 import한다`() {
        // 앱 자신의 코드(자기 루트 패키지)는 허용 — 규칙의 대상은 tradex 레이어 침범이다
        val apps = mapOf(
            "tradex-auth-service" to "io.tradex.",
            "tradex-member-service" to "io.tradex.",
            "tradex-registration-service" to "io.tradex.",
        )
        val bad = apps.flatMap { (module, ownPackage) ->
            mainFilesOf(module)
                .filter { it.packagee?.name?.contains(".domain") == true }
                .flatMap { file ->
                    file.imports.map { it.name }
                        .filter {
                            it.startsWith("io.tradex.") &&
                                !it.startsWith("io.tradex.core.") &&
                                !it.startsWith(ownPackage)
                        }
                        .map { "${file.path} -> $it" }
                }
        }
        withClue("도메인 패키지가 core 외의 tradex 레이어를 import:\n${bad.joinToString("\n")}") { bad.shouldBeEmpty() }
    }
}
