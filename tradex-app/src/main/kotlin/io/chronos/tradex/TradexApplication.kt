package io.chronos.tradex

import io.chronos.runtime.EnableChronos
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableChronos
class TradexApplication

fun main(args: Array<String>) {
    runApplication<TradexApplication>(*args)
}
