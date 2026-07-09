plugins {
    id("tradex.kotlin-library")
    id("org.jetbrains.kotlin.plugin.spring")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.14"))
}
