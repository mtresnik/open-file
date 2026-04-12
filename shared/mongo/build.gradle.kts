plugins {
    alias(libs.plugins.kotlin.jvm)
    kotlin("plugin.serialization") version "2.3.0"
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

    implementation("commons-cli:commons-cli:1.4")

    implementation("org.slf4j:slf4j-api:2.0.9")

    // Source: https://mvnrepository.com/artifact/org.slf4j/slf4j-simple
    implementation("org.slf4j:slf4j-simple:2.0.17")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3")

    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:5.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation(platform("org.mongodb:mongodb-driver-bom:5.6.4"))
    implementation("org.mongodb:mongodb-driver-sync")

    implementation("com.github.docker-java:docker-java-core:3.3.4")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.3.4")

    implementation(project(":shared:core"))
}

kotlin {
    jvmToolchain(11)
}

tasks.test {
    useJUnitPlatform()
}