package io.chronos.ontology

import io.chronos.membrane.EventSchemaRegistry

/**
 * 코드의 @EventSchema ↔ YAML 레지스트리 diff.
 * Gradle `ontologyValidate` 태스크는 소스 스캔으로 같은 검사를 빌드 타임에 수행한다.
 */
object OntologyValidator {
    fun diff(ontology: OntologyRegistry, eventSchemas: EventSchemaRegistry): List<String> {
        val problems = mutableListOf<String>()
        val codeTypes = eventSchemas.all().associateBy { it.type }

        for (code in codeTypes.values) {
            val declared = ontology.schemaFor(code.type)
            if (declared == null) {
                problems += "코드의 이벤트 '${code.type}' v${code.latestVersion}이 온톨로지에 미등록"
                continue
            }
            if (declared.version != code.latestVersion) {
                problems += "'${code.type}' 버전 불일치: 코드 v${code.latestVersion} vs 온톨로지 v${declared.version}"
            }
        }
        for (declared in ontology.schemas.values) {
            if (declared.type !in codeTypes) {
                problems += "온톨로지의 '${declared.type}'에 대응하는 @EventSchema 클래스가 코드에 없음"
            }
        }
        return problems
    }
}

/**
 * Bounded Context 경계를 넘는 이벤트 발행 가드: **APPROVED 스키마만 허용**.
 * 위반이 하나라도 있으면 애플리케이션 기동을 실패시킨다 (fail-fast).
 *
 * 검사 방향은 코드 → 온톨로지 단방향이다: 레지스트리는 앱 스코프지만 온톨로지 디렉토리는
 * 저장소(여러 앱) 공용이므로, "온톨로지에만 있는 타입"은 다른 앱의 것일 수 있다.
 * 그 방향의 drift는 전 모듈을 스캔하는 빌드 게이트(ontologyValidate)가 잡는다.
 */
object OntologyGuard {
    fun enforce(ontology: OntologyRegistry, eventSchemas: EventSchemaRegistry) {
        val problems = mutableListOf<String>()
        for (code in eventSchemas.all()) {
            val declared = ontology.schemaFor(code.type)
            when {
                declared == null ->
                    problems += "코드의 이벤트 '${code.type}' v${code.latestVersion}이 온톨로지에 미등록"
                declared.version != code.latestVersion ->
                    problems += "'${code.type}' 버전 불일치: 코드 v${code.latestVersion} vs 온톨로지 v${declared.version}"
                declared.status != SchemaStatus.APPROVED ->
                    problems += "'${code.type}'은 status=${declared.status} — APPROVED 전에는 발행 불가"
            }
        }

        if (problems.isNotEmpty()) {
            throw OntologyViolationException(
                "온톨로지 위반으로 기동 중단 (${problems.size}건):\n" + problems.joinToString("\n") { " - $it" },
            )
        }
    }
}

class OntologyViolationException(message: String) : RuntimeException(message)
