plugins {
    id("chronos.spring-library")
}

dependencies {
    api(project(":chronos-core"))
    api(project(":chronos-membrane"))
    api(project(":chronos-saga"))
    api(project(":chronos-router"))
    api(project(":chronos-cell"))
    api(project(":chronos-ontology"))

    // 아키텍처 규칙 검증 (프로젝트 전체 스캔이라 전 레이어를 아는 이 모듈에 둔다)
    testImplementation("com.lemonappdev:konsist:0.17.3")
}
