package io.chronos.runtime

import org.springframework.context.annotation.Import

/** CHRONOS 전 레이어를 조립한다. `@SpringBootApplication` 옆에 붙이는 것 하나로 끝. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(ChronosConfiguration::class)
annotation class EnableChronos
