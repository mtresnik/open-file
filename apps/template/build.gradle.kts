plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.sqldelight)
    `maven-publish`
    kotlin("plugin.serialization")
}

group = "org.open.file.template"
version = libs.versions.project.get()

// Disambiguate from `:shared:template`'s jar — both projects are
// named `template`, which collides in the CLI distribution's
// `lib/` directory without an explicit archives name.
base {
    archivesName.set("template-sql")
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("org.open.file.template")
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
            groupId = "org.open.file.template"
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
    implementation(project(":shared:template"))
    testImplementation(project(":shared:template"))
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
