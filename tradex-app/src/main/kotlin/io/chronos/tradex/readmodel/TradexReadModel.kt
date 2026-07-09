package io.chronos.tradex.readmodel

import io.chronos.cell.CellFabric
import io.chronos.core.event.AggregateId
import io.chronos.core.event.DomainEvent
import io.chronos.core.event.EventRecord
import io.chronos.router.InMemoryProjectionOffsetStore
import io.chronos.router.Projection
import io.chronos.router.Projector
import io.chronos.tradex.auth.contract.UserRegistered
import io.chronos.tradex.auth.contract.UserRegistrationRevoked
import io.chronos.tradex.member.contract.MemberCreated
import io.chronos.tradex.member.contract.MemberCreationRevoked
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import org.springframework.context.SmartLifecycle

/**
 * 이메일/전화해시 유니크 인덱스 (DECISIONS.md D20).
 * 애그리게잇 경계를 넘는 유니크 제약은 조회 모델의 일이다. 등록 직전 [catchUp]으로
 * 동기 캐치업해 단일 JVM에서는 사실상 선형화된 검사를 얻는다.
 * TODO(v2): reservation aggregate로 동시 등록 race 제거
 */
class EmailIndexProjection : Projection {
    override val name = "email-index"
    private val byEmail = ConcurrentHashMap<String, AggregateId>()
    private val byUser = ConcurrentHashMap<AggregateId, String>()

    override fun apply(record: EventRecord<DomainEvent>) {
        when (val event = record.event) {
            is UserRegistered -> {
                byEmail[event.email] = record.aggregateId
                byUser[record.aggregateId] = event.email
            }
            is UserRegistrationRevoked -> byUser.remove(record.aggregateId)?.let(byEmail::remove)
            else -> Unit
        }
    }

    fun userIdOf(email: String): AggregateId? = byEmail[email]
}

class PhoneIndexProjection : Projection {
    override val name = "phone-index"
    private val byHash = ConcurrentHashMap<String, AggregateId>()
    private val byMember = ConcurrentHashMap<AggregateId, String>()

    override fun apply(record: EventRecord<DomainEvent>) {
        when (val event = record.event) {
            is MemberCreated -> {
                byHash[event.phoneNumberHash] = record.aggregateId
                byMember[record.aggregateId] = event.phoneNumberHash
            }
            is MemberCreationRevoked -> byMember.remove(record.aggregateId)?.let(byHash::remove)
            else -> Unit
        }
    }

    fun memberIdOf(phoneNumberHash: String): AggregateId? = byHash[phoneNumberHash]
}

class TradexReadModel(fabric: CellFabric) : SmartLifecycle {
    val emailIndex = EmailIndexProjection()
    val phoneIndex = PhoneIndexProjection()
    private val offsets = InMemoryProjectionOffsetStore()

    private val projectors: List<Projector> = fabric.cells.flatMap { (cellId, cell) ->
        listOf(emailIndex, phoneIndex).map { projection ->
            Projector(cell.store, CellScoped("${projection.name}-cell$cellId", projection), offsets, cellId)
        }
    }

    /** 대기 중인 이벤트를 전부 반영한다 — 유니크 검사 직전에 호출. */
    fun catchUp() {
        projectors.forEach { projector ->
            while (projector.catchUpOnce() > 0) Unit
        }
    }

    private var running = false

    override fun start() {
        projectors.forEach { it.start(pollInterval = 20.milliseconds) }
        running = true
    }

    override fun stop() {
        projectors.forEach { it.stop() }
        running = false
    }

    override fun isRunning(): Boolean = running

    /** 공유 프로젝션 인스턴스에 셀별 오프셋 이름을 부여하는 래퍼. */
    private class CellScoped(override val name: String, private val delegate: Projection) : Projection {
        override fun apply(record: EventRecord<DomainEvent>) = delegate.apply(record)
    }
}
