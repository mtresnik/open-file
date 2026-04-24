plugins {
    kotlin("jvm")
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
    // Needed for parsing restic's `--json` output via kotlinx.serialization.
    // Same plugin version the rest of the repo uses in its shared modules.
    kotlin("plugin.serialization") version "2.1.20"
}

group = "org.open.file"
version = projectVersion()

// jpackage's Msi / Dmg builders reject anything that isn't a bare
// `major.minor.build` triple (no `-SNAPSHOT`, no `g0deadbe` hash).
// Strip any qualifier, and fall back to 1.0.0 when the derived
// version doesn't match semver numerics (e.g. on a commit past the
// last tag, `git describe` returns `0.0.1-3-g0deadbe` which is
// useful for jars but not for OS installers).
val nativePackageVersion: String = projectVersion()
    .substringBefore('-')
    .takeIf { it.matches(Regex("""\d+\.\d+\.\d+""")) } ?: "1.0.0"

// macOS DMG validator additionally requires MAJOR >= 1 — `0.x.y`
// versions are rejected at jpackage configure time even when you're
// only building the .deb / .msi. MSI and DEB tolerate 0.x.y, so
// only the macOS-specific override gets bumped. The headline
// `packageVersion` (and `AppInfo.VERSION`) keep the real version
// the rest of the build sees.
val macOSPackageVersion: String = run {
    val major = nativePackageVersion.substringBefore('.').toIntOrNull() ?: 1
    if (major < 1) "1.0.0" else nativePackageVersion
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Shared domain + service layer. The UI calls these services directly
    // (no HTTP, no separate process) via a coroutine dispatcher.
    //
    // :shared and :shared:core are needed on the COMPILE classpath because
    // every module in the repo pulls them in with `implementation` (not
    // `api`), so their types — e.g. the `Service<T>` supertype of
    // TemplateService in :shared:core — aren't transitively exposed through
    // :shared:template alone. Without them you get:
    //   "Cannot access 'Service' which is a supertype of 'TemplateService'"
    implementation(project(":shared"))
    implementation(project(":shared:core"))
    implementation(project(":shared:snapshot"))
    implementation(project(":shared:template"))
    implementation(project(":shared:backup"))
    // Zip I/O — used by both BackupRepository.restore (routed through
    // ArchiveExtractor directly, bypassing the deprecated
    // BackupExtractor shim) and zipScaffold (for bundled template
    // resources). Pure utility module with no domain types.
    implementation(project(":shared:archive"))

    // DAO impls are discovered via ServiceLoader at runtime; the UI
    // never references them from code, so runtimeOnly keeps the
    // compile surface clean while still putting the
    // META-INF/services entries on the classpath. The app is SQL-
    // only now; the Mongo backend was removed along with its
    // subprojects and the HTTP layer.
    runtimeOnly(project(":apps:snapshot"))
    runtimeOnly(project(":apps:template"))
    runtimeOnly(project(":apps:backup"))

    // Coroutines — the Swing module brings kotlinx-coroutines-core (for
    // Dispatchers.IO) and adds Dispatchers.Swing so we can hand results back
    // to the AWT event thread after blocking service calls.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    // kotlinx.serialization — used by ResticBackend to parse `restic
    // snapshots --json`. Shared modules pull their own copy in with
    // `implementation`, which doesn't leak across module boundaries, so
    // we declare it explicitly here.
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
            packageVersion = nativePackageVersion

            macOS {
                // DMG rejects MAJOR=0; route DMG-bound builds
                // through the bumped fallback while letting MSI /
                // DEB use the real project version.
                packageVersion = macOSPackageVersion
            }

            // JDK modules the packaged `jlink` runtime needs. Without
            // these, the bundled JRE is trimmed to java.base +
            // java.desktop and SQLite JDBC fails with NoClassDefFoundError
            // on java.sql.Connection — which ServiceLoader wraps as
            // "Provider BackupSQLDao could not be instantiated".
            //
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
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
    }
}

// Emit a `version.properties` resource so AppInfo.kt can read the
// runtime version without a hardcoded constant drifting from the
// Gradle version. The file lives in build/generated/resources and
// is wired into the main source set's resources below.
val generateVersionProperties = tasks.register("generateVersionProperties") {
    val outputDir = layout.buildDirectory.dir("generated/resources/version")
    outputs.dir(outputDir)
    val ver = project.version.toString()
    inputs.property("version", ver)

    doLast {
        val dir = outputDir.get().asFile.also { it.mkdirs() }
        dir.resolve("version.properties").writeText("version=$ver\n")
    }
}

sourceSets.main {
    resources.srcDir(generateVersionProperties)
}