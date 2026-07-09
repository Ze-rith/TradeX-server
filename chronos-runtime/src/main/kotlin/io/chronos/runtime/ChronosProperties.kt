package io.chronos.runtime

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "chronos")
data class ChronosProperties(
    /** IN_MEMORY | POSTGRES */
    val storage: Storage = Storage.IN_MEMORY,
    val cellCount: Int = 3,
    val virtualNodes: Int = 128,
    /** 세션 토큰 HMAC 키. 데모 기본값 — 실제 배포라면 반드시 주입할 것. */
    val sessionSecret: String = "chronos-demo-secret",
    /** read-your-writes 폴링 대기 상한 (ms). 초과 시 503 + Retry-After. */
    val rywTimeoutMs: Long = 2_000,
    /** ontology YAML 디렉토리. 존재하지 않으면 기동 실패. */
    val ontologyDir: String = "ontology",
    /** POSTGRES 셀 파티션 테이블 프리픽스 — 여러 앱이 한 DB를 공유할 때 분리용. */
    val tablePrefix: String = "event_store",
) {
    enum class Storage { IN_MEMORY, POSTGRES }
}
