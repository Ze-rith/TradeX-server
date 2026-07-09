package io.chronos.tradex.registration

import io.chronos.runtime.EnableChronos
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableChronos
class RegistrationServiceApplication

fun main(args: Array<String>) {
    runApplication<RegistrationServiceApplication>(*args)
}
