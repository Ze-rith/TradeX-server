plugins {
    id("chronos.kotlin-library")
}

dependencies {
    api(project(":chronos-membrane"))
    implementation("org.yaml:snakeyaml:2.3")
}
