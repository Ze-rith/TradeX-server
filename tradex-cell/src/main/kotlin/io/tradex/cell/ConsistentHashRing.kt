package io.tradex.cell

import io.tradex.core.event.AggregateId
import java.security.MessageDigest
import java.util.TreeMap

class ConsistentHashRing(cellIds: Collection<Int>, virtualNodes: Int = 128) {
    private val ring = TreeMap<Long, Int>()

    init {
        require(cellIds.isNotEmpty()) { "at least one cell required" }
        require(virtualNodes >= 1)
        for (cellId in cellIds) {
            repeat(virtualNodes) { replica -> ring[hash("cell-$cellId#vnode-$replica")] = cellId }
        }
    }

    fun route(aggregateId: AggregateId): Int {
        val point = hash(aggregateId.toString())
        return ring.ceilingEntry(point)?.value ?: ring.firstEntry().value
    }

    private fun hash(key: String): Long {
        val digest = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (digest[i].toLong() and 0xFF)
        }
        return value
    }
}
