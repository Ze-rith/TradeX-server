package io.tradex.membrane

import io.tradex.core.event.DomainEvent
import kotlin.reflect.KClass

data class RegisteredSchema(
    val type: String,
    val latestVersion: Int,
    val eventClass: KClass<out DomainEvent>,
)

/**
 * (type, version) ↔ 이벤트 클래스 매핑. [register]는 @EventSchema를 읽고,
 * 애노테이션을 붙일 수 없는 모듈(예: core testFixtures)은 명시적 오버로드를 쓴다.
 */
class EventSchemaRegistry {
    private val byType = LinkedHashMap<String, RegisteredSchema>()
    private val byClass = LinkedHashMap<KClass<*>, RegisteredSchema>()

    fun register(eventClass: KClass<out DomainEvent>): RegisteredSchema {
        val annotation = eventClass.annotations.filterIsInstance<EventSchema>().firstOrNull()
            ?: throw SchemaRegistrationException("${eventClass.qualifiedName}에 @EventSchema가 없습니다")
        return register(annotation.type, annotation.version, eventClass)
    }

    fun register(type: String, latestVersion: Int, eventClass: KClass<out DomainEvent>): RegisteredSchema {
        require(latestVersion >= 1) { "version must be >= 1: $type" }
        byType[type]?.let {
            if (it.eventClass != eventClass) {
                throw SchemaRegistrationException("type '$type'이 ${it.eventClass}와 $eventClass 양쪽에 선언됨")
            }
        }
        val schema = RegisteredSchema(type, latestVersion, eventClass)
        byType[type] = schema
        byClass[eventClass] = schema
        return schema
    }

    fun schemaFor(type: String): RegisteredSchema =
        byType[type] ?: throw UnknownEventTypeException("미등록 이벤트 타입: $type")

    fun schemaFor(eventClass: KClass<out DomainEvent>): RegisteredSchema =
        byClass[eventClass] ?: throw UnknownEventTypeException("미등록 이벤트 클래스: ${eventClass.qualifiedName}")

    fun all(): List<RegisteredSchema> = byType.values.toList()
}

class SchemaRegistrationException(message: String) : RuntimeException(message)
class UnknownEventTypeException(message: String) : RuntimeException(message)
