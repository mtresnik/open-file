plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.sqldelight)
    kotlin("plugin.serialization")
}

group = "org.open.file.backup"
version = libs.versions.project.get()

// Disambiguate from `:shared:backup`'s jar — both projects are
// named `backup`, which collides in the CLI distribution's
// `lib/` directory without an explicit archives name.
base {
    archivesName.set("backup-sql")
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("org.open.file.backup")
        }
    }
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation(libs.guava)
    implementation(libs.sqldelight.driver)
    // Match :apps:snapshot:sql exactly — same driver version, same URL form.
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    implementation("org.slf4j:slf4j-api:2.0.9")

    implementation(project(":shared"))
    implementation(project(":shared:core"))
    implementation(project(":shared:sql"))
    implementation(project(":shared:backup"))

    testImplementation(project(":shared"))
    testImplementation(project(":shared:core"))
    testImplementation(project(":shared:sql"))
    testImplementation(project(":shared:backup"))
}

configurations.all {
    resolutionStrategy {
        force("org.xerial:sqlite-jdbc:3.49.1.0")
    }
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
    filter {
        isFailOnNoMatchingTests = false
    }
}

tasks.matching { it.name == "verifyMainDatabaseMigration" }.configureEach {
    enabled = false
}

tasks.withType<Tar> { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
tasks.withType<Zip> { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
