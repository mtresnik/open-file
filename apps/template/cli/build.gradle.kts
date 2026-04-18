group = "org.open.file"
version = libs.versions.project.get()

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.sqldelight)

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
            groupId = "org.open.file.template"
            artifactId = "cli"
            version = libs.versions.project.get()
        }
    }
}

repositories {
    // Use Maven Central for resolving dependencies.
    google()
    mavenCentral()
}

val buildTarget = findProperty("build.target")?.toString()?.takeIf { it.isNotBlank() } ?: "sql"

dependencies {

    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Use the JUnit 5 integration.
    testImplementation(libs.junit.jupiter.engine)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // This dependency is used by the application.
    implementation(libs.guava)

    implementation(libs.sqldelight.driver)

    implementation("commons-cli:commons-cli:1.4")

    implementation("org.slf4j:slf4j-api:2.0.9")

    implementation(project(":shared"))
    testImplementation(project(":shared"))
    implementation(project(":shared:core"))
    testImplementation(project(":shared:core"))

    implementation(project(":shared:template"))

    when (buildTarget) {
        "mongo" -> {
            implementation(project(":shared:mongo"))
            implementation(project(":apps:template:mongo"))
        }
        "sql"   -> {
            implementation(project(":shared:sql"))
            implementation(project(":apps:template:sql"))
        }
        else    -> throw UnsupportedOperationException("Unsupported database option: $buildTarget!")
    }
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}

application {
    // Define the main class for the application.
    mainClass = "org.open.file.template.AppKt"
}

// fat jar
tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.open.file.template.AppKt"
    }
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Tar> { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
tasks.withType<Zip> { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }

tasks.named<Test>("test") {
    useJUnitPlatform()
    filter {
        isFailOnNoMatchingTests = false
    }
}