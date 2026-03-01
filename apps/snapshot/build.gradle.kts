plugins {
    kotlin("jvm")
}

group = "org.open.file"
version = libs.versions.project

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(11)
}

tasks.test {
    useJUnitPlatform()
}