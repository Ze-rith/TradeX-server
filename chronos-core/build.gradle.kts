plugins {
    id("chronos.kotlin-library")
    `java-test-fixtures`
}

// L0: 순수 Kotlin. 프로덕션 의존성 금지 (kotlin-stdlib/JDK만).
// testFixtures(MutableClock, Product 미니 도메인)는 다른 모듈 테스트에서 재사용.
