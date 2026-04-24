package org.open.file.ui.util

/**
 * Canonical "how do I install this?" URL per tool. Read by the
 * detail pane's Install button when the user needs a toolchain they
 * don't already have.
 *
 * Prefer the official install page where one exists; prefer version-
 * manager installers (rustup, nvm) over raw binaries for languages that
 * have them, since users of this app are likely to want multiple
 * versions anyway.
 */
object ToolInstaller {

    /** Install URL for the tool key, or null if we don't know one. */
    fun installUrlFor(tool: String): String? = when (tool) {
        VersionDetector.TOOL_KOTLIN -> "https://kotlinlang.org/docs/command-line.html"
        VersionDetector.TOOL_GRADLE -> "https://gradle.org/install/"
        VersionDetector.TOOL_JAVA   -> "https://adoptium.net/"
        VersionDetector.TOOL_RUST, VersionDetector.TOOL_CARGO -> "https://rustup.rs/"
        VersionDetector.TOOL_NODE   -> "https://nodejs.org/en/download"
        VersionDetector.TOOL_PYTHON -> "https://www.python.org/downloads/"
        VersionDetector.TOOL_GO     -> "https://go.dev/doc/install"
        VersionDetector.TOOL_RESTIC -> "https://restic.readthedocs.io/en/stable/020_installation.html"
        else -> null
    }
}
