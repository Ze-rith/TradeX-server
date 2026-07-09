package io.tradex.membrane

import com.fasterxml.jackson.databind.JsonNode

interface Upcaster {
    val type: String
    val fromVersion: Int
    fun upcast(old: JsonNode): JsonNode
}

class UpcasterChain(upcasters: List<Upcaster>) {
    private val byTypeAndVersion: Map<String, Map<Int, Upcaster>> =
        upcasters.groupBy { it.type }.mapValues { (type, list) ->
            list.groupBy { it.fromVersion }.mapValues { (version, sameVersion) ->
                require(sameVersion.size == 1) { "type '$type' fromVersion=$version upcaster가 중복 등록됨" }
                sameVersion.single()
            }
        }

    fun upcast(type: String, fromVersion: Int, toVersion: Int, payload: JsonNode): JsonNode {
        require(fromVersion <= toVersion) {
            "저장 버전($fromVersion)이 최신 버전($toVersion)보다 높음: $type — 코드가 구버전인가?"
        }
        var node = payload
        for (version in fromVersion until toVersion) {
            val upcaster = byTypeAndVersion[type]?.get(version)
                ?: throw MissingUpcasterException(type, version, version + 1)
            node = upcaster.upcast(node)
        }
        return node
    }
}

class MissingUpcasterException(type: String, from: Int, to: Int) :
    RuntimeException("upcaster 결번: $type v$from → v$to")
