package org.open.file.ui.util

import org.open.file.utils.FileSystemUtils
import java.io.File
import java.util.Properties

/**
 * Static metadata about the running app — version string, link
 * targets, filesystem paths the About / Settings sections surface to
 * the user.
 *
 * Version is read from a `version.properties` resource generated
 * by the desktop-ui build at package time, so it always matches
 * the Gradle project version (which in turn comes from the git
 * tag via `projectVersion()`).
 */
object AppInfo {
    /** Human-facing version string — generated from the git tag at build time. */
    val VERSION: String = loadVersionFromResources()

    /** Display name of the app used in About / tray tooltips. */
    const val DISPLAY_NAME: String = "OpenFile"

    /**
     * Upstream repo / release page. Opens in the user's browser from
     * the About section.
     */
    const val HOMEPAGE_URL: String = "https://github.com/"

    /**
     * Root of the app's persistent data directory. Derived from the
     * log file's grandparent (`logs/error.log` → `~/.open-file/`)
     * to avoid adding a new public accessor to FileSystemUtils.
     */
    fun dataDirectory(): File =
        FileSystemUtils.home("logs/error.log").parentFile?.parentFile
            ?: File(System.getProperty("user.home"), ".open-file")

    /** Error log path the [org.open.file.ui.notifications.ErrorReporter] appends to. */
    fun errorLogFile(): File = FileSystemUtils.home("logs/error.log")

    private fun loadVersionFromResources(): String =
        runCatching {
            AppInfo::class.java.getResourceAsStream("/version.properties")?.use { stream ->
                Properties().apply { load(stream) }.getProperty("version")
            }?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: "dev"
}
