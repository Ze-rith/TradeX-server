package io.tradex.membrane

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class EventSchema(val type: String, val version: Int)
