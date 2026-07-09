package io.tradex.membrane

import com.fasterxml.jackson.databind.JsonNode

/**
 * 버전 n → n+1 스키마 변환. 한 단계만 담당하며 체인 조립은 [UpcasterChain]이 한다.
 */
interface Upcaster {
    val type: String
    val fromVersion: Int
    fun upcast(old: JsonNode): JsonNode
}

/**
 * type별 upcaster를 fromVersion 순으로 정렬해 두고, 저장 버전 → 최신 버전 경로를 적용한다.
 * 경로에 결번이 있으면 [MissingUpcasterException] — 조용히 옛 스키마를 통과시키지 않는다.
 */
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
