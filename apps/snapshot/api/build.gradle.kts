plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "org.open.file.snapshot"
version = libs.versions.project.get()

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation(project(":shared"))
    testImplementation(project(":shared"))
    implementation(project(":shared:core"))
    testImplementation(project(":shared:core"))
    implementation(project(":shared:snapshot"))
    testImplementation(project(":shared:snapshot"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.serialization.json)

}

kotlin {
    jvmToolchain(11)
}

tasks.test {
    useJUnitPlatform()
}