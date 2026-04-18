plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "org.open.file.template"
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
    implementation(project(":shared:template"))
    testImplementation(project(":shared:template"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.serialization.json)

}

kotlin {
    jvmToolchain(11)
}

tasks.test {
    useJUnitPlatform()
}