plugins {
    alias(libs.plugins.kotlin.jvm)
    id("app.cash.sqldelight") version "2.3.2"
    `maven-publish`
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

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation(libs.guava)
    implementation("app.cash.sqldelight:sqlite-driver:2.3.2")
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

tasks.withType<Tar> { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
tasks.withType<Zip> { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }