plugins {
    id("chronos.kotlin-library")
}

dependencies {
    api(project(":chronos-core"))
    implementation(project(":chronos-membrane"))
}
