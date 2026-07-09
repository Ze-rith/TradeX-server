package io.tradex.runtime

import org.springframework.context.annotation.Import

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(TradexConfiguration::class)
annotation class EnableTradex
