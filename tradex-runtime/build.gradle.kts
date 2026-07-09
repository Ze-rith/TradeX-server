plugins {
    id("tradex.spring-library")
}

dependencies {
    api(project(":tradex-core"))
    api(project(":tradex-membrane"))
    api(project(":tradex-saga"))
    api(project(":tradex-router"))
    api(project(":tradex-cell"))
    api(project(":tradex-ontology"))

    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-jdbc")

    testImplementation("com.lemonappdev:konsist:0.17.3")
}
