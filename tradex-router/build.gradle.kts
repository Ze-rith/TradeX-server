plugins {
    id("tradex.kotlin-library")
}

dependencies {
    api(project(":tradex-core"))

    testImplementation(testFixtures(project(":tradex-core")))
}
