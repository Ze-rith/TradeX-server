plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    kotlin("plugin.jpa") version "1.9.25"

    id("org.springframework.boot") version "3.5.14"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "zerith"
version = "0.0.1-SNAPSHOT"
description = "tradexServer"

extra["flyway.version"] = "11.10.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // =========================
    // WEB / CORE
    // =========================
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // =========================
    // SECURITY (JWT / AUTH)
    // =========================
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // JWT (발급 + 검증)
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // =========================
    // DATABASE
    // =========================
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // =========================
    // REDIS (TOKEN / CACHE)
    // =========================
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // =========================
    // CACHE / MONITORING
    // =========================
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // =========================
    // AOP (LOGGING / SECURITY ASPECT)
    // =========================
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // =========================
    // DB MIGRATION
    // =========================
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // =========================
    // KOTLIN SUPPORT
    // =========================
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // =========================
    // TEST
    // =========================
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property"
        )
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
