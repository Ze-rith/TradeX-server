plugins {
    id("chronos.kotlin-library")
}

dependencies {
    api(project(":chronos-core"))

    testImplementation(testFixtures(project(":chronos-core")))
}
