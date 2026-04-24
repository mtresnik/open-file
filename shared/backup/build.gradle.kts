plugins {
    alias(libs.plugins.kotlin.jvm)
    kotlin("plugin.serialization")
}

group = "org.open.file.shared"
version = projectVersion()

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation(project(":shared"))
    implementation(project(":shared:core"))
    // Backups piggy-back on the snapshot tree-builder + node model so we can
    // record what was in the directory at archive time (hash digest + counts)
    // without duplicating the directory-walk logic.
    implementation(project(":shared:snapshot"))
    // Zip I/O — extractor logic lives in :shared:archive now so template
    // scaffolding can reuse it. Eventually BackupArchiver's zip writer
    // should also migrate there; left in place for now because it's
    // tangled with the snapshot tree.
    implementation(project(":shared:archive"))

    testImplementation(project(":shared:core"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
