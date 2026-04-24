package org.open.file.ui.util

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Best-effort detection of installed versions for the tools backing the
 * packaged templates.
 *
 * Strategy per tool:
 *  1. Walk the common version-manager directories (SDKMAN, rustup, nvm,
 *     pyenv, etc.) — those give us *every* installed version, not just
 *     the one currently on PATH.
 *  2. Fall through to a `<tool> --version` subprocess as a single-version
 *     fallback when no version manager is present.
 *  3. De-duplicate (version-manager entries + PATH version can overlap)
 *     and sort newest-first lexicographically (good enough for semver-ish).
 *
 * Every call is cached per-process so opening three packaged templates
 * doesn't shell out nine times. The cache is populated lazily on first
 * read of a tool and never invalidated; restart the app if the user
 * installs a new toolchain mid-session.
 *
 * Subprocess launches time out after 3s so a misbehaving PATH shim
 * can't hang the UI thread (all calls are expected to come from IO anyway).
 */
object VersionDetector {

    /** Every tool the packaged templates know about. Stable strings — used as map keys. */
    const val TOOL_KOTLIN = "kotlin"
    const val TOOL_GRADLE = "gradle"
    const val TOOL_RUST = "rust"
    const val TOOL_CARGO = "cargo"
    const val TOOL_NODE = "node"
    const val TOOL_PYTHON = "python"
    const val TOOL_GO = "go"
    const val TOOL_JAVA = "java"
    const val TOOL_RESTIC = "restic"

    private val cache = mutableMapOf<String, List<String>>()
    private val home = System.getProperty("user.home").orEmpty()

    /** All installed versions for [tool], newest-first. Empty list means nothing detected. */
    fun detectVersions(tool: String): List<String> = synchronized(cache) {
        // Only cache *successful* detections. An empty result means
        // "not installed right now" — if the user installs the tool
        // later we want the next read to pick it up without requiring
        // an app restart, so we re-run detection for empty entries.
        cache[tool]?.let { if (it.isNotEmpty()) return@synchronized it }

        val raw = when (tool) {
            TOOL_KOTLIN -> sdkmanVersions("kotlin") + listOfNotNull(parseKotlinc())
            TOOL_GRADLE -> sdkmanVersions("gradle") + listOfNotNull(parseGradle())
            TOOL_RUST, TOOL_CARGO -> rustupVersions() + listOfNotNull(parseRustc())
            TOOL_NODE -> nvmVersions() + listOfNotNull(parseNode())
            TOOL_PYTHON -> pyenvVersions() + listOfNotNull(parsePython())
            TOOL_GO -> goSdkVersions() + listOfNotNull(parseGo())
            TOOL_JAVA -> sdkmanVersions("java") + listOfNotNull(parseJava())
            TOOL_RESTIC -> listOfNotNull(parseRestic())
            else -> emptyList()
        }
        val result = raw.filter { it.isNotBlank() }.distinct().sortedDescending()
        if (result.isNotEmpty()) cache[tool] = result
        result
    }

    /**
     * Forget every cached detection so the next [detectVersions] call
     * re-runs the PATH / version-manager scan. Wire this to a Refresh
     * button in the UI for users who installed a new toolchain without
     * restarting the app.
     */
    fun clearCache() = synchronized(cache) { cache.clear() }

    // ──────────────────────────────────────────────
    // Version-manager directory scanners
    // ──────────────────────────────────────────────

    private fun sdkmanVersions(candidate: String): List<String> {
        // SDKMAN layout: ~/.sdkman/candidates/<tool>/<version>/
        // "current" is a symlink we want to skip.
        val dir = File(home, ".sdkman/candidates/$candidate")
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isDirectory && it.name != "current" }
            ?.map { it.name }
            ?: emptyList()
    }

    private fun rustupVersions(): List<String> {
        // rustup toolchain dirs: ~/.rustup/toolchains/<version-triple>/
        // Names look like "1.75.0-aarch64-apple-darwin" or "stable-x86_64-…"
        // so we extract the leading semver-ish prefix.
        val dir = File(home, ".rustup/toolchains")
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dirName ->
                val name = dirName.name
                Regex("""^((?:stable|beta|nightly|\d+(?:\.\d+)+))""").find(name)?.value
            }
            ?.distinct()
            ?: emptyList()
    }

    private fun nvmVersions(): List<String> {
        // ~/.nvm/versions/node/v18.17.0/ etc.
        val dir = File(home, ".nvm/versions/node")
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name.removePrefix("v") }
            ?: emptyList()
    }

    private fun pyenvVersions(): List<String> {
        // pyenv keeps installed versions under ~/.pyenv/versions/<version>/
        val dir = File(home, ".pyenv/versions")
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?: emptyList()
    }

    private fun goSdkVersions(): List<String> {
        // `go install golang.org/dl/go1.X` drops SDKs under ~/sdk/go1.X/
        val dir = File(home, "sdk")
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("go") }
            ?.map { it.name.removePrefix("go") }
            ?: emptyList()
    }

    // ──────────────────────────────────────────────
    // Subprocess fallbacks
    // ──────────────────────────────────────────────

    private fun parseKotlinc(): String? = run("kotlinc", "-version")?.let {
        Regex("""kotlinc-?(?:jvm)?\s+(\S+)""").find(it)?.groupValues?.get(1)
    }

    private fun parseGradle(): String? = run("gradle", "--version")?.let {
        Regex("""Gradle\s+(\S+)""").find(it)?.groupValues?.get(1)
    }

    private fun parseRustc(): String? = run("rustc", "--version")?.let {
        Regex("""rustc\s+(\S+)""").find(it)?.groupValues?.get(1)
    }

    private fun parseNode(): String? = run("node", "--version")?.trim()?.removePrefix("v")?.takeIf { it.isNotBlank() }

    private fun parsePython(): String? =
        (run("python3", "--version") ?: run("python", "--version"))
            ?.let { Regex("""Python\s+(\S+)""").find(it)?.groupValues?.get(1) }

    private fun parseGo(): String? = run("go", "version")?.let {
        Regex("""go(\S+)""").find(it)?.groupValues?.get(1)
    }

    private fun parseJava(): String? = run("java", "-version")?.let {
        // `java -version` writes to stderr; we merge streams below, so
        // both `version "17.0.1"` and `openjdk version "21.0.1"` match.
        Regex("""version\s+"([^"]+)"""").find(it)?.groupValues?.get(1)
    }

    private fun parseRestic(): String? = run("restic", "version")?.let {
        // Example output: "restic 0.16.4 compiled with go1.21.6 on darwin/arm64"
        Regex("""restic\s+(\S+)""").find(it)?.groupValues?.get(1)
    }

    private fun run(vararg args: String): String? = try {
        // On Windows the PATH entries for tools like Gradle, npm, etc.
        // often resolve to `.bat` / `.cmd` scripts rather than real
        // executables. Java's ProcessBuilder *doesn't* auto-append
        // those extensions, so `ProcessBuilder("gradle", ...)` fails
        // with "command not found" even though `gradle.bat` is right
        // there on PATH. Wrapping the call through `cmd /c` delegates
        // the lookup to Windows' native resolver, which handles all
        // the extensions correctly.
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val cmd = if (os.contains("win")) arrayOf("cmd", "/c") + args else args
        val pb = ProcessBuilder(*cmd).redirectErrorStream(true)
        val p = pb.start()
        if (!p.waitFor(3, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            null
        } else {
            val text = p.inputStream.bufferedReader().use { it.readText() }
            // Treat non-zero exit as "not installed" — both Unix shells
            // and Windows cmd write error text to stderr (which we
            // merge into stdout) when the command isn't found, and
            // without this check that error text would leak through
            // to callers whose parser doesn't have a strict regex
            // (notably parseNode, which did a plain `removePrefix("v")`
            // and passed `'node' is not recognized...` through intact).
            if (p.exitValue() != 0) null else text
        }
    } catch (_: Throwable) {
        null  // tool isn't on PATH or exec permission denied — treat as "not installed"
    }
}
