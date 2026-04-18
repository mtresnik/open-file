plugins {
    alias(libs.plugins.kotlin.jvm)
    kotlin("plugin.serialization") version "2.1.20"
}

group = "org.open.file.shared"
version = libs.versions.project.get()

repositories {
    mavenCentral()
}

dependencies {
    // Use the Kotlin Test integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Use the JUnit 5 integration.
    testImplementation(libs.junit.jupiter.engine)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // This dependency is used by the application.
    implementation(libs.guava)

    implementation(libs.sqldelight.driver)

    implementation("commons-cli:commons-cli:1.4")

    implementation("org.slf4j:slf4j-api:2.0.9")

    // Source: https://mvnrepository.com/artifact/org.slf4j/slf4j-simple
    implementation("org.slf4j:slf4j-simple:2.0.17")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3")
}

kotlin {
    jvmToolchain(11)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    filter {
        isFailOnNoMatchingTests = false
    }
}