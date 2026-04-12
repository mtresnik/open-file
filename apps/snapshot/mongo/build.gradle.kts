plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

group = "org.open.file.snapshot"
version = libs.versions.project

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
            artifactId = "mongo"
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

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("commons-cli:commons-cli:1.4")
    implementation("org.slf4j:slf4j-api:2.0.9")

    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:5.2.0")
    implementation(platform("org.mongodb:mongodb-driver-bom:5.6.4"))
    implementation("org.mongodb:mongodb-driver-sync")

    implementation(project(":shared"))
    testImplementation(project(":shared"))
    implementation(project(":shared:core"))
    testImplementation(project(":shared:core"))

    implementation(project(":shared:snapshot"))
    testImplementation(project(":shared:snapshot"))

    implementation(project(":shared:mongo"))
    testImplementation(project(":shared:mongo"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}