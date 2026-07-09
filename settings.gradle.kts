plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "tradex"

include(
    "tradex-core",
    "tradex-membrane",
    "tradex-saga",
    "tradex-router",
    "tradex-cell",
    "tradex-ontology",
    "tradex-runtime",
    "tradex-auth-service",
    "tradex-member-service",
    "tradex-registration-service",
)
