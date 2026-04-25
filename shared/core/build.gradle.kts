plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "org.open.file.shared"
version = libs.versions.project.get()

dependencies {
    testImplementation(kotlin("test"))

    implementation("commons-cli:commons-cli:1.4")

    implementation("org.slf4j:slf4j-api:2.0.9")

    // Source: https://mvnrepository.com/artifact/org.slf4j/slf4j-simple
    implementation("org.slf4j:slf4j-simple:2.0.17")
}

kotlin {
    jvmToolchain(11)
}

tasks.test {
    useJUnitPlatform()
}