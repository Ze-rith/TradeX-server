package io.tradex.ontology

import io.tradex.membrane.EventSchemaRegistry

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
