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
    "example-app",
)
