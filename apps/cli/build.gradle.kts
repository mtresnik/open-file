group = "org.open.file"
version = projectVersion()

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    `maven-publish`
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/mtresnik/open-file")
            credentials {
                username = System.getenv("USERNAME") ?: findProperty("gpr.user")?.toString() ?: "mtresnik"
                password = System.getenv("TOKEN") ?: findProperty("gpr.token")?.toString()
            }
        }
    }
    publications {
        create<MavenPublication>("gpr") {
            from(components["java"])
        }
        withType<MavenPublication>().all {
            groupId = "org.open.file"
            artifactId = "cli"
            version = projectVersion()
        }
    }
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation(libs.guava)
    implementation(libs.sqldelight.driver)

    implementation("commons-cli:commons-cli:1.4")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.17")

    implementation(project(":shared"))
    implementation(project(":shared:core"))
    implementation(project(":shared:snapshot"))
    implementation(project(":shared:template"))
    implementation(project(":shared:backup"))
    implementation(project(":shared:archive"))
    implementation(project(":shared:sql"))

    // Domain DAO impls — loaded via ServiceLoader at runtime.
    implementation(project(":apps:snapshot"))
    implementation(project(":apps:template"))
    implementation(project(":apps:backup"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}

kotlin {
    compilerOptions {
        // kotlin.time.Clock / Instant are still experimental in 2.1.20.
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
    }
}

application {
    mainClass = "openfile.AppKt"
    applicationName = "openfile"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    filter { isFailOnNoMatchingTests = false }
}

// Distribution: `./gradlew :apps:cli:installDist` produces a
// launcher at build/install/openfile/bin/openfile. Run it as
// `openfile snapshot --list`. A fat-jar variant would need the
// Shadow plugin (hand-rolled overrides on tasks.jar create a
// task-graph cycle with the domain modules' artifacts).
