plugins {
    id("chronos.spring-library")
    id("org.springframework.boot") version "3.5.14"
}

dependencies {
    implementation(project(":chronos-runtime"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// README의 curl 시나리오는 저장소 루트에서 실행된다 (ontology/ 상대 경로)
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir
}
