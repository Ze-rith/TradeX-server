package io.tradex.membrane

/**
 * 이벤트 클래스의 현재 (type, version) 선언.
 * 저장된 과거 버전은 [Upcaster] 체인을 거쳐 항상 이 최신 버전으로 승격된 뒤에만 코어에 전달된다.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class EventSchema(val type: String, val version: Int)
