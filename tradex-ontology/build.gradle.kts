plugins {
    id("tradex.kotlin-library")
}

dependencies {
    api(project(":tradex-membrane"))
    implementation("org.yaml:snakeyaml:2.3")
}
