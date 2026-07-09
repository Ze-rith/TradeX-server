package io.tradex.router

enum class ConsistencyLevel(val headerValue: String) {
    STRONG("strong"),
    READ_YOUR_WRITES("read-your-writes"),
    EVENTUAL("eventual"),
    ;

    companion object {
        fun fromHeader(value: String?): ConsistencyLevel {
            if (value.isNullOrBlank()) return EVENTUAL
            return entries.firstOrNull { it.headerValue.equals(value.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "지원하지 않는 X-Consistency 값 '$value' (허용: ${entries.joinToString { it.headerValue }})",
                )
        }
    }
}
