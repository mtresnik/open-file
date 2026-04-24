package org.open.file.ui.data

import org.open.file.ui.screens.TemplateUiModel
import java.io.File

/**
 * Built-in, immutable templates the app ships with. They always appear
 * at the top of the Templates list and can't be deleted — the [id]
 * prefix [PACKAGED_ID_PREFIX] is the marker the repository and detail
 * pane use to enforce that.
 *
 * [tools] names the tools whose installed versions the detail pane
 * should surface via `VersionDetector`. A packaged template can declare
 * zero tools (the "Bare directory" case) or several (the "Kotlin +
 * Gradle" case).
 *
 * [scaffold] writes the template's starter files into [destination].
 * Called from the repo's `scaffold(...)` on Generate; [selectedVersions]
 * is a map of tool -> version string picked by the user in the detail
 * pane, so generated config files can bake in the right toolchain.
 * Default implementation just creates the directory and drops a README
 * pointing at the install pages — useful for templates without a
 * hardcoded scaffold, and a safety net for misconfigured ones.
 */
data class PackagedTemplate(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val tags: List<String>,
    /**
     * Tools surfaced in the detail pane's "Detected versions" section.
     * Every entry gets a dropdown the user can pick from; the choice
     * gets baked into generated config files via [scaffold].
     */
    val tools: List<String> = emptyList(),
    /**
     * Subset of [tools] the user actually needs installed before the
     * generated project can be built. Distinct from [tools] because
     * some toolchains (notably the Kotlin CLI) are *templated* into the
     * scaffold but aren't required to build it — Gradle and Maven ship
     * their own Kotlin compiler, so kotlinc on PATH is optional.
     *
     * Defaults to [tools] — so a template without an explicit override
     * treats everything it lists as required, the safe behaviour.
     */
    val requiredTools: List<String> = tools,
    /**
     * True when the scaffold's init command (e.g. `npm create vite
     * @latest <name>`) creates the `<name>/` subdirectory itself —
     * the repo passes the *parent* (user-picked output dir) as the
     * lambda's `destination` in that case and trusts the tool to
     * populate the right subfolder underneath.
     *
     * False (default) means the tool writes into whatever directory
     * it's handed: the repo pre-wraps the user's output dir in
     * `<output>/<projectName>/` before invoking the lambda so files
     * never splatter directly into the user's Documents folder.
     */
    val createsOwnTargetDir: Boolean = false,
    /**
     * [projectName] is the user-supplied identifier from the Generate
     * panel — threaded through so scaffolds can bake it into config
     * files (build.gradle.kts `rootProject.name`, Cargo.toml
     * `package.name`, go.mod module path, etc.) without guessing from
     * the output directory's basename. Already non-blank + sanitised
     * by the caller.
     *
     * Contract: `destination` is the directory the scaffold should
     * populate. When [createsOwnTargetDir] is false this is
     * `<userOutput>/<projectName>/` — already resolved by the repo.
     * When true, it's the user's chosen output dir and the tool
     * creates `<projectName>/` underneath.
     */
    val scaffold: (destination: File, selectedVersions: Map<String, String>, projectName: String) -> Unit = { dest, _, _ ->
        dest.mkdirs()
        File(dest, "README.md").writeText(genericReadme())
    },
)

/** Fallback README for packaged templates that don't define a richer scaffold. */
private fun genericReadme(): String = """
    # New project

    Created from a packaged open-file template.

    Install the required tools from the detail pane's install links, then
    follow the usual `init` / `new` command for your framework of choice.
""".trimIndent()

/** Prefix every packaged template id with this so the runtime can tell them apart from user templates. */
const val PACKAGED_ID_PREFIX = "packaged:"

/** True if [templateId] names a packaged template; covers both domain-layer and UI-layer consumers. */
fun isPackagedTemplateId(templateId: String): Boolean = templateId.startsWith(PACKAGED_ID_PREFIX)

/**
 * Localised description for a packaged template.
 *
 * Returns the translated string from [strings] when [templateId] names
 * a known packaged template, or `null` for user templates (whose
 * descriptions are user-authored free text and not translatable).
 *
 * We resolve at the UI layer rather than baking translation into
 * [PackagedTemplate] so the data layer stays free of a `Strings`
 * dependency — `PackagedTemplate.description` keeps the English
 * fallback for non-UI callers (scaffold fallback README, logs, etc.),
 * while every composable that renders a description swaps in the
 * locale-specific version via this helper.
 */
fun localizedPackagedTemplateDescription(
    templateId: String,
    strings: org.open.file.ui.i18n.Strings,
): String? = when (templateId) {
    "${PACKAGED_ID_PREFIX}kotlin-gradle" -> strings.templateDescKotlinGradle
    "${PACKAGED_ID_PREFIX}ktor-server" -> strings.templateDescKtorServer
    "${PACKAGED_ID_PREFIX}spring-boot" -> strings.templateDescSpringBoot
    "${PACKAGED_ID_PREFIX}rust-cargo" -> strings.templateDescRustCargo
    "${PACKAGED_ID_PREFIX}node-express" -> strings.templateDescNodeExpress
    "${PACKAGED_ID_PREFIX}react-vite" -> strings.templateDescReactVite
    "${PACKAGED_ID_PREFIX}python-fastapi" -> strings.templateDescPythonFastapi
    "${PACKAGED_ID_PREFIX}go-module" -> strings.templateDescGoModule
    else -> null
}

private object T {
    const val KOTLIN = org.open.file.ui.util.VersionDetector.TOOL_KOTLIN
    const val GRADLE = org.open.file.ui.util.VersionDetector.TOOL_GRADLE
    const val JAVA = org.open.file.ui.util.VersionDetector.TOOL_JAVA
    const val RUST = org.open.file.ui.util.VersionDetector.TOOL_RUST
    const val CARGO = org.open.file.ui.util.VersionDetector.TOOL_CARGO
    const val NODE = org.open.file.ui.util.VersionDetector.TOOL_NODE
    const val PYTHON = org.open.file.ui.util.VersionDetector.TOOL_PYTHON
    const val GO = org.open.file.ui.util.VersionDetector.TOOL_GO
}

/** Helper: write a text file, creating parent dirs as needed. */
private fun write(dest: File, relative: String, text: String) {
    val f = File(dest, relative)
    f.parentFile?.mkdirs()
    f.writeText(text.trimIndent())
}

/**
 * Common preamble: ensure [dest] exists and is empty-ish. Returns true
 * when we're safe to run a tool-init command into it, false when we
 * should fall back to writing files ourselves (e.g. because the user
 * pointed at a non-empty directory — init commands typically error
 * on that and we'd rather succeed with a minimal scaffold than fail
 * outright).
 */
private fun prepareEmpty(dest: File): Boolean {
    dest.mkdirs()
    return dest.listFiles().isNullOrEmpty()
}

/** Sanitise a directory name for use as a package / module identifier. */
private fun sanitize(name: String, fallback: String): String =
    name.lowercase().replace(Regex("[^a-z0-9_]"), "-").trim('-').ifBlank { fallback }

// ──────────────────────────────────────────────
// Zip-backed template scaffolds
// ──────────────────────────────────────────────
//
// Large templates that would be tedious to hand-code (full React
// Native starters, Spring Boot apps with vendor'd deps, projects
// containing binary assets) can ship as a zip bundled under
// `desktop-ui/src/main/resources/templates/<id>.zip`. The
// `zipScaffold(...)` factory returns a ready-made scaffold lambda that
// extracts the zip into the destination.
//
// Shared code with backups: the extraction reuses the same
// `BackupExtractor` that the Restore Backup flow calls — one zip
// pipeline, two call sites. Keeps the "open zip, walk entries, write
// files, guard against zip-slip" logic in one place, so a fix for a
// tricky archive (weird entry names, Windows line endings on extract,
// etc.) improves both surfaces at once.

/**
 * File extensions we treat as text when applying placeholder
 * substitution in zip-extracted templates. Missed extensions get
 * copied verbatim — safer than scrambling a binary file because we
 * didn't know it was JPEG.
 */
private val TEXT_EXTENSIONS = setOf(
    "kt", "kts", "java", "groovy",
    "xml", "json", "yaml", "yml", "toml", "properties", "gradle",
    "md", "markdown", "txt", "adoc", "rst",
    "html", "htm", "css", "scss",
    "js", "jsx", "mjs", "cjs", "ts", "tsx",
    "go", "rs", "py", "rb", "php", "swift",
    "c", "h", "cpp", "hpp", "cs",
    "sh", "bash", "zsh", "fish",
    "conf", "cfg", "ini", "env",
)

/** Filenames without extensions that are still conventionally text. */
private val TEXT_FILENAMES = setOf(
    "dockerfile", "makefile", "readme", "license", "gitignore",
    "gitattributes", "editorconfig", "nvmrc", "npmrc",
)

/**
 * Factory: returns a `scaffold` lambda that extracts a bundled zip
 * resource into the destination directory and performs placeholder
 * substitution on text files.
 *
 * [resourcePath] is a JVM classpath path, typically
 * `/templates/<id>.zip`. The file must be on `desktop-ui`'s
 * `src/main/resources/` path so it's bundled into the distributable.
 *
 * Substitution pass walks the extracted tree, rewriting these tokens
 * inside text files:
 *   - `{{PROJECT_NAME}}` → the user's project name
 *   - `{{PROJECT_NAME_SAFE}}` → `sanitize(projectName)` — safe as a
 *     package / module identifier (e.g. for build.gradle.kts).
 *
 * Pass [substitute = false] to skip the substitution pass entirely
 * when the bundled template is already generic (e.g. a framework
 * starter you want to hand the user unchanged).
 */
/**
 * Walk [dir] and rewrite template placeholder tokens inside every
 * text file. Binary files (anything not in [TEXT_EXTENSIONS] /
 * [TEXT_FILENAMES]) are left untouched so icons, fonts, etc. don't
 * get corrupted by string replacement.
 *
 * Shared by `zipScaffold` (for bundled packaged templates) and the
 * `TemplateRepository.scaffold` user-template branch — keeps the
 * substitution rules identical across surfaces, so a user copying a
 * packaged template, tweaking it, and re-saving as a user template
 * gets the same tokens working.
 *
 * Tokens:
 *  - `{{PROJECT_NAME}}` → [projectName] verbatim.
 *  - `{{PROJECT_NAME_SAFE}}` → sanitised for package / module ids.
 */
fun applyTemplatePlaceholders(dir: File, projectName: String) {
    if (!dir.exists() || !dir.isDirectory) return
    val safe = sanitize(projectName, "app")
    dir.walkTopDown().filter { it.isFile }.forEach { file ->
        if (isTextFile(file)) {
            val content = runCatching { file.readText() }.getOrNull() ?: return@forEach
            val replaced = content
                .replace("{{PROJECT_NAME}}", projectName)
                .replace("{{PROJECT_NAME_SAFE}}", safe)
            if (content != replaced) file.writeText(replaced)
        }
    }
}

fun zipScaffold(
    resourcePath: String,
    substitute: Boolean = true,
): (File, Map<String, String>, String) -> Unit = { dest, _, projectName ->
    dest.mkdirs()

    // ArchiveExtractor takes a File, not a Stream — so we spill the
    // classpath resource to a temp file first. Small price for
    // reusing the production-hardened extractor (zip-slip guards,
    // empty-dir handling, proper entry-path separator logic).
    //
    // Resource lookup routes through `PackagedTemplate::class.java`
    // because `::class` isn't valid on a top-level function
    // reference in Kotlin — we need a real JVM class whose
    // classloader is the :desktop-ui module's. Any class declared
    // in this file works; PackagedTemplate is the obvious one.
    val stream = PackagedTemplate::class.java.getResourceAsStream(resourcePath)
        ?: error("Bundled template resource not found: $resourcePath")
    val tempZip = File.createTempFile("template-", ".zip")
    tempZip.deleteOnExit()
    try {
        stream.use { input ->
            tempZip.outputStream().use { output -> input.copyTo(output) }
        }
        org.open.file.archive.ArchiveExtractor.extract(
            archive = tempZip,
            destination = dest,
            format = org.open.file.archive.ArchiveFormat.ZIP,
        )
    } finally {
        tempZip.delete()
    }

    if (substitute) applyTemplatePlaceholders(dest, projectName)
}

private fun isTextFile(f: File): Boolean {
    val ext = f.extension.lowercase()
    if (ext in TEXT_EXTENSIONS) return true
    val nameLower = f.name.lowercase()
    if (nameLower in TEXT_FILENAMES) return true
    // Extensionless files that start with a dot (e.g. .editorconfig,
    // .gitattributes) fall through to the TEXT_FILENAMES check once
    // the leading dot is stripped.
    val stripped = nameLower.trimStart('.')
    return stripped in TEXT_FILENAMES
}

/**
 * The full catalogue. Order matters — this is the display order in
 * the Templates list. Each entry carries a minimal-but-working scaffold
 * so Generate actually produces something runnable rather than an
 * empty directory with a stub README.
 */
val PACKAGED_TEMPLATES: List<PackagedTemplate> = listOf(
    PackagedTemplate(
        id = "${PACKAGED_ID_PREFIX}kotlin-gradle",
        name = "Kotlin + Gradle",
        description = "Kotlin JVM project backed by the Gradle Kotlin DSL. Detects installed Kotlin / Gradle / Java versions on your system.",
        icon = "kotlin",
        tags = listOf("kotlin", "gradle", "jvm"),
        tools = listOf(T.KOTLIN, T.GRADLE, T.JAVA),
        // Gradle downloads and manages its own Kotlin compiler via the
        // `kotlin("jvm")` plugin — kotlinc on PATH is templated-only,
        // not required for build. Same reasoning would apply to Maven
        // if we add a Maven variant. Gradle + JDK are the real build
        // requirements.
        requiredTools = listOf(T.GRADLE, T.JAVA),
        scaffold = { dest, versions, projectName ->
            val java = versions[T.JAVA]?.substringBefore('.')?.toIntOrNull() ?: 17
            val canUseInit = prepareEmpty(dest)
            val result = if (canUseInit) {
                // `gradle init` with all the right flags is non-
                // interactive and produces a canonical, batteries-
                // included scaffold (wrapper, test stubs, CI-friendly
                // layout) that we shouldn't duplicate by hand.
                org.open.file.ui.util.ToolExecutor.run(
                    command = listOf(
                        "gradle", "init",
                        "--type", "kotlin-application",
                        "--dsl", "kotlin",
                        "--project-name", projectName,
                        "--package", "com.example",
                        "--test-framework", "junit-jupiter",
                        "--java-version", java.toString(),
                        "--no-split-project",
                    ),
                    workingDir = dest,
                )
            } else null

            if (result?.success != true) {
                // Fallback — user has no gradle on PATH, or init failed
                // for some other reason. Write a minimal skeleton
                // equivalent to what `gradle init` would have produced.
                val kotlin = versions[T.KOTLIN] ?: "1.9.22"
                write(dest, "settings.gradle.kts", """rootProject.name = "$projectName"""")
                write(dest, "build.gradle.kts", """
                    plugins {
                        kotlin("jvm") version "$kotlin"
                        application
                    }
                    repositories { mavenCentral() }
                    kotlin { jvmToolchain($java) }
                    application { mainClass.set("MainKt") }
                """)
                write(dest, "src/main/kotlin/Main.kt", """
                    fun main() { println("Hello from Kotlin!") }
                """)
            }
        },
    ),

    PackagedTemplate(
        id = "${PACKAGED_ID_PREFIX}ktor-server",
        name = "Ktor Server",
        description = "Kotlin HTTP server with Ktor + Netty. Inherits from Kotlin + Gradle — extend to customise.",
        icon = "ktor",
        tags = listOf("kotlin", "ktor", "server"),
        tools = listOf(T.KOTLIN, T.GRADLE),
        // Ktor builds through Gradle which brings its own Kotlin —
        // CLI is optional for the same reason as the base Kotlin
        // template.
        requiredTools = listOf(T.GRADLE),
        scaffold = { dest, versions, projectName ->
            // Ktor doesn't have a first-party CLI init — their project
            // generator is web-only at start.ktor.io. We write a
            // minimal scaffold directly: `build.gradle.kts` with the
            // Ktor plugin + deps, plus a single-file Application.kt.
            val kotlin = versions[T.KOTLIN] ?: "1.9.22"
            dest.mkdirs()
            write(dest, "settings.gradle.kts", """rootProject.name = "$projectName"""")
            write(dest, "build.gradle.kts", """
                plugins {
                    kotlin("jvm") version "$kotlin"
                    application
                }
                repositories { mavenCentral() }
                dependencies {
                    implementation("io.ktor:ktor-server-core:2.3.7")
                    implementation("io.ktor:ktor-server-netty:2.3.7")
                }
                application { mainClass.set("ApplicationKt") }
            """)
            write(dest, "src/main/kotlin/Application.kt", """
                import io.ktor.server.application.*
                import io.ktor.server.engine.*
                import io.ktor.server.netty.*
                import io.ktor.server.response.*
                import io.ktor.server.routing.*

                fun main() {
                    embeddedServer(Netty, port = 8080) {
                        routing { get("/") { call.respondText("Hello, Ktor!") } }
                    }.start(wait = true)
                }
            """)
        },
    ),

    PackagedTemplate(
        id = "${PACKAGED_ID_PREFIX}spring-boot",
        name = "Spring Boot",
        description = "Spring Boot service skeleton (Java or Kotlin). Picks up the JDK version from your system.",
        icon = "spring",
        tags = listOf("spring", "boot", "jvm"),
        tools = listOf(T.JAVA, T.GRADLE),
        scaffold = { dest, versions, projectName ->
            // Spring's canonical init is Spring Initializr (web) or
            // `spring init` CLI. Neither is a universal developer
            // install, so we write a minimal Gradle + Spring Boot
            // skeleton directly. Users who want the full Initializr
            // output are nudged there via the detail pane's Install
            // button, which opens adoptium/gradle install pages.
            val java = versions[T.JAVA]?.substringBefore('.')?.toIntOrNull() ?: 17
            dest.mkdirs()
            write(dest, "settings.gradle.kts", """rootProject.name = "$projectName"""")
            write(dest, "build.gradle.kts", """
                plugins {
                    java
                    id("org.springframework.boot") version "3.2.1"
                    id("io.spring.dependency-management") version "1.1.4"
                }
                repositories { mavenCentral() }
                java { toolchain { languageVersion.set(JavaLanguageVersion.of($java)) } }
                dependencies {
                    implementation("org.springframework.boot:spring-boot-starter-web")
                }
            """)
            write(dest, "src/main/java/com/example/Application.java", """
                package com.example;
                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                @SpringBootApplication
                public class Application {
                    public static void main(String[] args) { SpringApplication.run(Application.class, args); }
                }
            """)
        },
    ),

    PackagedTemplate(
        id = "${PACKAGED_ID_PREFIX}rust-cargo",
        name = "Rust (Cargo)",
        description = "Cargo-managed Rust binary. Lists rustup toolchains alongside the stock rustc on PATH.",
        icon = "rust",
        tags = listOf("rust", "cargo"),
        tools = listOf(T.RUST, T.CARGO),
        scaffold = { dest, versions, projectName ->
            val canUseInit = prepareEmpty(dest)
            // `cargo init <path> --name <name>` is the canonical entry
            // point — it writes Cargo.toml + src/main.rs for us, sets
            // edition = "2021", and respects `.gitignore` policy.
            val result = if (canUseInit) {
                org.open.file.ui.util.ToolExecutor.run(
                    command = listOf("cargo", "init", dest.absolutePath, "--name", projectName),
                    workingDir = dest.parentFile ?: dest,
                )
            } else null

            if (result?.success != true) {
                // Fallback when cargo isn't on PATH. Same minimal
                // layout cargo would have produced.
                val rust = versions[T.RUST] ?: "1.75.0"
                write(dest, "Cargo.toml", """
                    [package]
                    name = "$projectName"
                    version = "gi0.1.0"
                    edition = "2021"

                    [dependencies]
                """)
                write(dest, "src/main.rs", """
                    fn main() { println!("Hello from Rust!"); }
                """)
                write(dest, "rust-toolchain.toml", """
                    [toolchain]
                    channel = "$rust"
                """)
            } else {
                // cargo init already wrote Cargo.toml — layer the
                // user's chosen toolchain on top so `rustup` picks up
                // the version they selected.
                val rust = versions[T.RUST]
                if (!rust.isNullOrBlank()) {
                    write(dest, "rust-toolchain.toml", """
                        [toolchain]
                        channel = "$rust"
                    """)
                }
            }
        },
    ),

    PackagedTemplate(
        id = "${PACKAGED_ID_PREFIX}node-express",
        name = "Node + Express",
        description = "Node.js service with an Express router. Enumerates nvm-installed Node versions.",
        icon = "node",
        tags = listOf("node", "express", "javascript"),
        tools = listOf(T.NODE),
        scaffold = { dest, versions, projectName ->
            val node = versions[T.NODE] ?: "20"
            prepareEmpty(dest)
            // `npm init -y` is canonical — writes a default package.json.
            // We then append Express-specific bits (dep, start script,
            // example server). `npm install express` is skipped on
            // purpose: it requires network and can take a while; the
            // README tells the user to run it.
            val initResult = org.open.file.ui.util.ToolExecutor.run(
                command = listOf("npm", "init", "-y"),
                workingDir = dest,
            )

            if (initResult.success) {
                // Patch the generated package.json: rewrite "main",
                // add "start" script + express dep. Cheap JSON-y
                // regex patch — avoids pulling in a JSON library.
                val pkg = File(dest, "package.json")
                if (pkg.exists()) {
                    var text = pkg.readText()
                    text = text.replace(Regex("\"main\":\\s*\"[^\"]*\""), "\"main\": \"index.js\"")
                    if (!text.contains("\"scripts\"")) {
                        text = text.replace(
                            "\"main\": \"index.js\",",
                            "\"main\": \"index.js\",\n  \"scripts\": { \"start\": \"node index.js\" },",
                        )
                    }
                    if (!text.contains("\"dependencies\"")) {
                        text = text.trimEnd('\n', '}') + ",\n  \"dependencies\": { \"express\": \"^4.18.2\" }\n}\n"
                    }
                    pkg.writeText(text)
                }
            } else {
                // Fallback package.json when npm isn't installed.
                write(dest, "package.json", """
                    {
                      "name": "$projectName",
                      "version": "1.0.0",
                      "main": "index.js",
                      "engines": { "node": ">=$node" },
                      "scripts": { "start": "node index.js" },
                      "dependencies": { "express": "^4.18.2" }
                    }
                """)
            }
            write(dest, "index.js", """
                const express = require('express');
                const app = express();
                app.get('/', (_req, res) => res.send('Hello from Node!'));
                app.listen(3000, () => console.log('listening on :3000'));
            """)
            write(dest, ".nvmrc", node)
        },
    ),

    PackagedTemplate(
        id = "${PACKAGED_ID_PREFIX}react-vite",
        name = "React (Vite)",
        description = "React SPA scaffolded with Vite. Picks up the Node version you're targeting.",
        icon = "react",
        tags = listOf("react", "vite", "frontend"),
        tools = listOf(T.NODE),
        // Vite's own init creates `<projectName>/` — opt out of the
        // repo's pre-wrap so we don't end up with
        // `<output>/<projectName>/<projectName>/`.
        createsOwnTargetDir = true,
        scaffold = { dest, versions, projectName ->
            val node = versions[T.NODE] ?: "20"
            // `npm create vite@latest <name> --template react` is the
            // canonical bootstrapper. We run it from the user's
            // output dir (dest) and vite drops `<projectName>/`
            // underneath. If that path already exists empty, remove
            // it so vite doesn't bail on "directory already exists".
            dest.mkdirs()
            val projectDir = File(dest, projectName)
            if (projectDir.exists() && projectDir.listFiles().isNullOrEmpty()) {
                projectDir.delete()
            }
            val result = org.open.file.ui.util.ToolExecutor.run(
                command = listOf(
                    "npm", "create", "vite@latest", projectName,
                    "--yes", "--", "--template", "react",
                ),
                workingDir = dest,
                // Vite scaffold downloads the template over the
                // network; give it more headroom than a typical init.
                timeoutSeconds = 240,
            )

            if (!result.success) {
                // Fallback — write a minimal Vite-compatible layout
                // into the projectName subdir ourselves.
                projectDir.mkdirs()
                write(projectDir, "package.json", """
                    {
                      "name": "$projectName",
                      "private": true,
                      "version": "0.0.0",
                      "type": "module",
                      "engines": { "node": ">=$node" },
                      "scripts": { "dev": "vite", "build": "vite build" },
                      "dependencies": { "react": "^18.2.0", "react-dom": "^18.2.0" },
                      "devDependencies": { "@vitejs/plugin-react": "^4.2.1", "vite": "^5.0.10" }
                    }
                """)
                write(projectDir, "index.html", """
                    <!doctype html>
                    <html><body><div id="root"></div><script type="module" src="/src/main.jsx"></script></body></html>
                """)
                write(projectDir, "src/main.jsx", """
                    import React from 'react'
                    import { createRoot } from 'react-dom/client'
                    createRoot(document.getElementById('root')).render(<h1>Hello from React!</h1>)
                """)
            }
            // Always drop the .nvmrc into the project dir (vite's
            // own scaffold creates it too, so this only matters for
            // the fallback path — but writing twice is idempotent).
            write(projectDir, ".nvmrc", node)
        },
    ),

    PackagedTemplate(
        id = "${PACKAGED_ID_PREFIX}python-fastapi",
        name = "Python + FastAPI",
        description = "FastAPI service skeleton. Surfaces pyenv-managed Python versions and the system default.",
        icon = "python",
        tags = listOf("python", "fastapi", "api"),
        tools = listOf(T.PYTHON),
        scaffold = { dest, versions, _ ->
            // Python has no single canonical init. `pdm init` /
            // `poetry init` / `python -m venv` all exist but none are
            // universally installed. Stick with a minimal hand-written
            // skeleton — users can layer their preferred tool on top.
            // projectName isn't templated anywhere here (FastAPI's
            // app identifier is just the Python module name); ignored.
            val py = versions[T.PYTHON] ?: "3.11"
            dest.mkdirs()
            write(dest, "requirements.txt", "fastapi>=0.104\nuvicorn>=0.23\n")
            write(dest, "main.py", """
                from fastapi import FastAPI
                app = FastAPI()
                @app.get("/")
                def root():
                    return {"message": "Hello from FastAPI"}
            """)
            write(dest, ".python-version", py)
            write(dest, "README.md", """
                # FastAPI app (Python $py)

                ## Run
                    pip install -r requirements.txt
                    uvicorn main:app --reload
            """)
        },
    ),

    PackagedTemplate(
        id = "${PACKAGED_ID_PREFIX}go-module",
        name = "Go Module",
        description = "Plain Go module. Lists SDKs installed via `go install golang.org/dl/go1.X`.",
        icon = "go",
        tags = listOf("go", "golang"),
        tools = listOf(T.GO),
        scaffold = { dest, versions, projectName ->
            val module = projectName
            val canUseInit = prepareEmpty(dest)
            // `go mod init <module>` is canonical — it writes go.mod
            // with the module path and detects the installed Go
            // version automatically. We still hand-write main.go
            // because `go mod init` doesn't produce application source.
            val result = if (canUseInit) {
                org.open.file.ui.util.ToolExecutor.run(
                    command = listOf("go", "mod", "init", module),
                    workingDir = dest,
                )
            } else null

            if (result?.success != true) {
                // Fallback go.mod when `go` isn't on PATH.
                val go = versions[T.GO]?.substringBeforeLast('.')?.ifBlank { null } ?: "1.21"
                write(dest, "go.mod", """
                    module $module

                    go $go
                """)
            }
            write(dest, "main.go", """
                package main

                import "fmt"

                func main() {
                    fmt.Println("Hello from Go!")
                }
            """)
        },
    ),
)

/**
 * Resolve packaged templates to the UI model the screens render.
 * [detected] is passed in so the call site controls when the (slow,
 * shell-bound) detection runs. Hot paths use [toUiModelFast] instead,
 * which short-circuits detection; a background job then fills it in
 * and swaps the list items via [TemplateRepository.refreshDetectedVersions].
 */
fun PackagedTemplate.toUiModel(detected: Map<String, List<String>>): TemplateUiModel =
    TemplateUiModel(
        id = id,
        type = "directory",
        name = name,
        description = description,
        tags = tags,
        created = "Built-in",
        updated = "",
        config = emptyMap(),
        previewTree = null,
        icon = icon,
        isPackaged = true,
        baseTemplateId = null,
        tools = tools,
        requiredTools = requiredTools,
        detectedToolVersions = detected,
        selectedToolVersions = detected.mapValues { (_, vs) -> vs.firstOrNull() ?: "" },
    )

/**
 * Zero-shell-call form used on the initial render path. Tools appear
 * as "not detected" for a frame or two until the background detection
 * pass finishes — far better than freezing the UI thread behind 20+
 * sequential `--version` shellouts at launch.
 */
fun PackagedTemplate.toUiModelFast(): TemplateUiModel =
    toUiModel(detected = tools.associateWith { emptyList() })
