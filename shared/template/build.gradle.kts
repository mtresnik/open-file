plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "org.open.file.shared"
version = libs.versions.project.get()

dependencies {
    implementation(project(":shared"))

    implementation(project(":shared:core"))
    testImplementation(project(":shared:core"))

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}