plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20")

    implementation("org.jetbrains.kotlin:kotlin-allopen:2.2.20")

    implementation("org.yaml:snakeyaml:2.3")
}
