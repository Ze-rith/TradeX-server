plugins {
    id("tradex.spring-library")
}

dependencies {
    api(project(":tradex-core"))
    implementation(project(":tradex-membrane"))

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    testImplementation(testFixtures(project(":tradex-core")))
}
