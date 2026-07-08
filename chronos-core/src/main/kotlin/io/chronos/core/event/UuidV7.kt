package io.chronos.core.event

import java.security.SecureRandom
import java.util.UUID

/**
 * RFC 9562 UUIDv7 생성기.
 * 상위 48비트 = unix epoch millis → eventId 자체가 대략적 시간 순서를 가진다.
 */
object UuidV7 {
    private val random = SecureRandom()

    fun generate(nowMillis: Long = System.currentTimeMillis()): UUID {
        val randA = random.nextInt(1 shl 12).toLong()
        val msb = (nowMillis shl 16) or (0x7L shl 12) or randA
        val lsb = (0b10L shl 62) or (random.nextLong() and 0x3FFF_FFFF_FFFF_FFFFL)
        return UUID(msb, lsb)
    }

    /** UUIDv7의 타임스탬프(unix millis) 추출. */
    fun timestampOf(uuid: UUID): Long = uuid.mostSignificantBits ushr 16
}
