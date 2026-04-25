plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "org.open.file.shared"
version = libs.versions.project.get()

// Pure zip I/O: no domain-layer dependencies (no Backup, no Template,
// no Snapshot types). Consumers — `:shared:backup` for restore,
// `:desktop-ui` for scaffolding zip-bundled templates — adapt the
// results into whatever shape they need.
dependencies {
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
    filter {
        isFailOnNoMatchingTests = false
    }
}
