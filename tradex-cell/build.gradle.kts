plugins {
    id("tradex.kotlin-library")
}

dependencies {
    api(project(":tradex-core"))
    api(project(":tradex-membrane"))
    api(project(":tradex-saga"))
    api(project(":tradex-router"))

    testImplementation(testFixtures(project(":tradex-core")))
}
