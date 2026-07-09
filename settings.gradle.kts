plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "chronos"

include(
    "chronos-core",
    "chronos-membrane",
    "chronos-saga",
    "chronos-router",
    "chronos-cell",
    "chronos-ontology",
    "chronos-runtime",
    "tradex-auth-service",
    "tradex-member-service",
    "tradex-registration-service",
)
