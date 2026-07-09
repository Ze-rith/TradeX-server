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

    // 아키텍처 규칙 검증 (프로젝트 전체 스캔이라 전 레이어를 아는 이 모듈에 둔다)
    testImplementation("com.lemonappdev:konsist:0.17.3")
}
