group = "org.open.file"
version = libs.versions.project

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    alias(libs.plugins.kotlin.jvm)
    id("app.cash.sqldelight") version "2.2.1"

    // Apply the application plugin to add support for building a CLI application in Java.
    application
    `maven-publish`
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("org.open.file")
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
            groupId = project.group.toString()
            artifactId = "template"
            version = project.version.toString()
        }
    }
}

repositories {
    // Use Maven Central for resolving dependencies.
    google()
    mavenCentral()
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Use the JUnit 5 integration.
    testImplementation(libs.junit.jupiter.engine)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // This dependency is used by the application.
    implementation(libs.guava)

    implementation("app.cash.sqldelight:sqlite-driver:2.1.0")

    implementation("commons-cli:commons-cli:1.4")

    implementation("org.slf4j:slf4j-api:2.0.9")

    implementation(project(":shared"))
    testImplementation(project(":shared"))
}

tasks.named("compileTestJava") {
    dependsOn(tasks.getByPath(":shared:compileTestJava"))
}

tasks.named("test") {
    dependsOn(tasks.getByPath(":shared:compileTestJava"))
}


// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}

application {
    // Define the main class for the application.
    mainClass = "org.open.file.template.MainKt"
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}
