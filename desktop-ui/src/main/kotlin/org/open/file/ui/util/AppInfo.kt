package org.open.file.ui.util

import org.open.file.utils.FileSystemUtils
import java.io.File

/**
 * Static metadata about the running app. The version constant is
 * hand-bumped together with `libs.versions.project` and the
 * `packageVersion` in `desktop-ui/build.gradle.kts`.
 */
object AppInfo {
    /** Human-facing version string. Keep in sync with libs.versions.toml + desktop-ui packageVersion. */
    const val VERSION: String = "1.0.0"

    /** Display name of the app used in About / tray tooltips. */
    const val DISPLAY_NAME: String = "OpenFile"

    /**
     * Upstream repo / release page. Opens in the user's browser
     * from the About section.
     */
    const val HOMEPAGE_URL: String = "https://github.com/"

    /**
     * Root of the app's persistent data directory. Derived from
     * the log file's grandparent (`logs/error.log` →
     * `~/.open-file/`) to avoid adding a new public accessor to
     * FileSystemUtils.
     */
    fun dataDirectory(): File =
        FileSystemUtils.home("logs/error.log").parentFile?.parentFile
            ?: File(System.getProperty("user.home"), ".open-file")

    /** Error log path the [org.open.file.ui.notifications.ErrorReporter] appends to. */
    fun errorLogFile(): File = FileSystemUtils.home("logs/error.log")
}
