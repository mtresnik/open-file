plugins {
    kotlin("jvm")
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
    // Needed for parsing restic's `--json` output via kotlinx.serialization.
    kotlin("plugin.serialization") version "2.1.20"
}

group = "org.open.file"
version = libs.versions.project.get()

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Shared domain + service layer. The UI calls these services
    // directly via a coroutine dispatcher.
    implementation(project(":shared"))
    implementation(project(":shared:core"))
    implementation(project(":shared:snapshot"))
    implementation(project(":shared:template"))
    implementation(project(":shared:backup"))
    // Zip I/O — used by both BackupRepository.restore and zipScaffold.
    implementation(project(":shared:archive"))

    // DAO impls are discovered via ServiceLoader at runtime; the UI
    // never references them from code, so runtimeOnly keeps the
    // compile surface clean while still putting the
    // META-INF/services entries on the classpath.
    runtimeOnly(project(":apps:snapshot"))
    runtimeOnly(project(":apps:template"))
    runtimeOnly(project(":apps:backup"))

    // Coroutines — the Swing module brings kotlinx-coroutines-core
    // (for Dispatchers.IO) and adds Dispatchers.Swing so we can
    // hand results back to the AWT event thread after blocking
    // service calls.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

compose.desktop {
    application {
        mainClass = "org.open.file.ui.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "open-file"
            // Bump this together with `project` in libs.versions.toml
            // when cutting a release. jpackage's DMG validator
            // requires MAJOR >= 1, so don't drop below 1.0.0 here
            // even if the Gradle/Maven version is 0.x.y.
            packageVersion = "1.0.0"

            // JDK modules the packaged `jlink` runtime needs.
            //  - java.sql         SqlDelight's JdbcSqliteDriver
            //  - java.naming      JDBC's DriverManager classpath hooks
            //  - java.management  logging + coroutines diagnostic hooks
            //  - jdk.unsupported  sun.misc.Unsafe — pulled in transitively
            //                     by kotlinx-coroutines + sqlite-jdbc
            modules(
                "java.sql",
                "java.naming",
                "java.management",
                "jdk.unsupported",
            )

            windows {
                // Desktop + Start-menu shortcuts. Both are
                // surfaced as checkboxes in the MSI's "Custom
                // Setup" page if the user hits "Custom" — by
                // default they're checked, so a Next-Next-Finish
                // install creates both.
                shortcut = true
                menuGroup = "OpenFile"

                // Let the user pick the install directory during
                // setup. Without this the MSI silently installs
                // to `C:\Program Files\open-file\`.
                dirChooser = true

                // upgradeUuid is required for the MSI to recognise
                // newer versions as upgrades of older ones (rather
                // than as a separate side-by-side product). Pick
                // any random UUID once and never change it; if you
                // bump it, MSI treats every existing install as a
                // different product and you can't upgrade in place.
                //
                // Generated with `uuidgen` — replace this with a
                // value of your own if you fork the project so
                // your installer doesn't try to upgrade an
                // upstream OpenFile install.
                upgradeUuid = "9a8d1c12-3ad7-4b02-9e37-7b0a1c9f88c1"

                // Per-user install (writes to %LOCALAPPDATA%) so
                // the MSI doesn't need admin elevation. Set to
                // false if you want a system-wide install instead.
                perUserInstall = true
            }
        }

        // Compose Desktop wires ProGuard into the release packaging
        // task by default (`packageReleaseDistributionForCurrentOS`).
        // ProGuard 7.2.2 doesn't understand Guava's modern
        // `VarHandle` / `MethodHandle` internals and treats the
        // resulting unresolved references as fatal warnings, so the
        // release build dies during minification. We don't actually
        // need shrinking for an end-user desktop app — the binary
        // size win isn't worth the brittleness — so turn it off and
        // ship the unminified release.
        buildTypes.release.proguard {
            isEnabled.set(false)
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
    }
}
