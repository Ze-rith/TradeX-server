package io.tradex.registration

import io.tradex.runtime.EnableTradex
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableTradex
class RegistrationServiceApplication

fun main(args: Array<String>) {
    runApplication<RegistrationServiceApplication>(*args)
}
