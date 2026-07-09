package io.tradex.core.event

import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class UuidV7Test {
    @Test
    fun `version 7과 RFC variant 비트를 가진다`() {
        val uuid = UuidV7.generate()
        uuid.version() shouldBe 7
        uuid.variant() shouldBe 2
    }

    @Test
    fun `타임스탬프가 상위 48비트에 보존되어 시간순 정렬된다`() {
        val t1 = 1_700_000_000_000L
        val t2 = t1 + 1_000
        val earlier = UuidV7.generate(t1)
        val later = UuidV7.generate(t2)

        UuidV7.timestampOf(earlier) shouldBe t1
        UuidV7.timestampOf(later) shouldBe t2
        earlier.compareTo(later) shouldBeLessThan 0
    }
}
