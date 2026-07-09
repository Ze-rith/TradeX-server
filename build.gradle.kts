plugins {
    base
}

allprojects {
    group = "io.chronos"
    version = "0.1.0-SNAPSHOT"
}

// 코드의 @EventSchema ↔ ontology/*.yaml diff 검사. check에 연결되어 build를 게이트한다.
val ontologyValidate = tasks.register<OntologyValidateTask>("ontologyValidate") {
    ontologyDir.set(layout.projectDirectory.dir("ontology"))
    sourceFiles.setFrom(
        subprojects.map { sub -> fileTree(sub.projectDir.resolve("src/main/kotlin")) { include("**/*.kt") } },
    )
}

tasks.named("check") {
    dependsOn(ontologyValidate)
}
