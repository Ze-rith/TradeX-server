plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20")
    // provides org.jetbrains.kotlin.plugin.spring
    implementation("org.jetbrains.kotlin:kotlin-allopen:2.2.20")
}
