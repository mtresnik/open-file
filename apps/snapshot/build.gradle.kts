plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.sqldelight)
    `maven-publish`
    kotlin("plugin.serialization")
}

group = "org.open.file.snapshot"
version = libs.versions.project.get()

sqldelight {
    databases {
        create("Database") {
            packageName.set("org.open.file.snapshot")
        }
    }
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
            groupId = "org.open.file.snapshot"
            artifactId = "sql"
            version = libs.versions.project.get()
        }
    }
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation(libs.guava)
    implementation(libs.sqldelight.driver)
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    implementation("commons-cli:commons-cli:1.4")
    implementation("org.slf4j:slf4j-api:2.0.9")

    implementation(project(":shared"))
    testImplementation(project(":shared"))
    implementation(project(":shared:core"))
    testImplementation(project(":shared:core"))
    implementation(project(":shared:sql"))
    testImplementation(project(":shared:sql"))
    implementation(project(":shared:snapshot"))
    testImplementation(project(":shared:snapshot"))
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