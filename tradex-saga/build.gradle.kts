plugins {
    id("tradex.spring-library") // Spring 코드는 없고 Boot BOM(버전 관리)만 사용
}

dependencies {
    api(project(":tradex-core"))
    implementation(project(":tradex-membrane"))

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    testImplementation(testFixtures(project(":tradex-core")))
}
