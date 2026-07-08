plugins {
    id("chronos.kotlin-library")
}

dependencies {
    api(project(":chronos-core"))
    api(project(":chronos-membrane"))
    api(project(":chronos-saga"))
    api(project(":chronos-router"))

    testImplementation(testFixtures(project(":chronos-core")))
}
