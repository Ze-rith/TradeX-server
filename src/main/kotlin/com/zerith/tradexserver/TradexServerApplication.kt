package com.zerith.tradexserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TradexServerApplication

fun main(args: Array<String>) {
    runApplication<TradexServerApplication>(*args)
}
