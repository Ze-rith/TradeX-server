plugins {
    base
}

allprojects {
    group = "io.tradex"
    version = "0.1.0-SNAPSHOT"
}

val ontologyValidate = tasks.register<OntologyValidateTask>("ontologyValidate") {
    ontologyDir.set(layout.projectDirectory.dir("ontology"))
    sourceFiles.setFrom(
        subprojects.map { sub -> fileTree(sub.projectDir.resolve("src/main/kotlin")) { include("**/*.kt") } },
    )
}

tasks.named("check") {
    dependsOn(ontologyValidate)
}
