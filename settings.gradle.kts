pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        kotlin("jvm") version "2.1.20"
        kotlin("plugin.serialization") version "2.1.20"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "open-file"

include(
    ":shared",
    ":shared:core",
    ":shared:snapshot",
    ":shared:sql",
    ":shared:template",
    ":shared:backup",
    ":shared:archive",

    ":apps:snapshot",
    ":apps:template",
    ":apps:backup",

    // Single CLI binary: `openfile <domain> <verb>`.
    ":apps:cli",

    ":desktop-ui",
)
