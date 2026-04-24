package org.open.file.ui.util

import java.awt.Desktop
import java.io.File

/**
 * Open the host OS's file manager and highlight [target] when possible.
 *
 * Platform behaviour:
 *  - **macOS**: `open -R <path>` — Finder opens with the file selected.
 *  - **Windows**: `explorer.exe /select,<path>` — Explorer opens with the
 *    file highlighted in its parent directory.
 *  - **Linux**: no cross-DE equivalent to "reveal", so fall back to
 *    `xdg-open` on the parent directory.
 *  - **Other / failure**: fall through to [Desktop.open] against the parent
 *    directory, which covers most JVM-supported desktops.
 *
 * Returns `true` if we successfully kicked off a reveal; `false` if the
 * target doesn't exist or every strategy fell over. Callers should just
 * log on false — there's nothing meaningful to surface in the UI.
 */
fun revealInFileExplorer(target: File): Boolean {
    if (!target.exists()) return false

    // Strategy 1: JDK 9+ native reveal API. Handles paths with
    // spaces, unicode, and other quoting pitfalls because it talks
    // to the OS file manager directly rather than shelling out. The
    // Windows ProcessBuilder path below used to break on paths like
    // `C:\Users\mike\My Projects\foo` — ProcessBuilder auto-quotes
    // args containing spaces, producing `/select,"C:\..."` which
    // Explorer interprets as a single quoted path (including the
    // /select prefix) and falls back to opening Documents. Calling
    // `browseFileDirectory` on `desktop` goes through Shell32 on
    // Windows / Finder on macOS, with the file correctly selected.
    if (Desktop.isDesktopSupported()) {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
            val launched = runCatching {
                desktop.browseFileDirectory(target)
                true
            }.getOrDefault(false)
            if (launched) return true
            // Some platforms advertise support but throw on call
            // (older Linux JDK builds, some macOS JVM distros).
            // Fall through to the shell-out branch.
        }
    }

    val os = System.getProperty("os.name").orEmpty().lowercase()
    val isMac = os.contains("mac") || os.contains("darwin")
    val isWindows = os.contains("win")

    return try {
        when {
            isMac -> {
                ProcessBuilder("open", "-R", target.absolutePath).start()
                true
            }
            isWindows -> {
                // Fallback when BROWSE_FILE_DIR isn't supported: open
                // the parent directory rather than trying to /select
                // the file. Explorer's /select flag doesn't play nice
                // with ProcessBuilder's auto-quoting for spaced
                // paths — the target isn't highlighted, but the
                // correct directory opens, which is the usual
                // expectation. Shelling out through `cmd /c start`
                // lets cmd handle the quoting rather than
                // ProcessBuilder.
                val parent = target.parentFile ?: target
                ProcessBuilder("cmd", "/c", "start", "", parent.absolutePath).start()
                true
            }
            else -> {
                // Linux / BSD: no reliable "reveal the file" command
                // across file managers, so open the parent directory.
                // xdg-open handles spaces in paths correctly because
                // ProcessBuilder passes the path as a single arg.
                val parent = target.parentFile ?: return false
                ProcessBuilder("xdg-open", parent.absolutePath).start()
                true
            }
        }
    } catch (_: Throwable) {
        // Shell-out fell over — last-resort AWT Desktop.open on the
        // parent, which any JVM-supported desktop can at least open.
        runCatching {
            val parent = target.parentFile ?: return@runCatching null
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(parent)
                true
            } else null
        }.getOrNull() ?: false
    }
}

/**
 * Open [folder] directly in the host OS's file manager, displaying its
 * contents (not the parent directory with it selected).
 *
 * Sibling to [revealInFileExplorer], which is about highlighting a
 * file in its parent. Some callers — the About section's "Show data
 * folder" button, for example — want to enter the folder itself so
 * the user lands looking at its contents, not its name.
 *
 * Platform behaviour:
 *  - **All**: `Desktop.open(folder)` — tells the OS to open the
 *    directory with its default handler, which on every supported
 *    desktop is the native file manager viewing the directory's
 *    contents.
 *  - **Fallback**: shell out (`open`, `explorer`, `xdg-open`) against
 *    the directory itself on platforms / JVMs where Desktop isn't
 *    wired up.
 *
 * Returns `true` if a launch happened. Callers should treat `false` as
 * a soft failure and either toast or log — there's nothing useful to
 * retry.
 */
fun openFolderInFileExplorer(folder: File): Boolean {
    if (!folder.exists() || !folder.isDirectory) return false

    if (Desktop.isDesktopSupported()) {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.OPEN)) {
            val launched = runCatching {
                desktop.open(folder)
                true
            }.getOrDefault(false)
            if (launched) return true
        }
    }

    val os = System.getProperty("os.name").orEmpty().lowercase()
    val isMac = os.contains("mac") || os.contains("darwin")
    val isWindows = os.contains("win")

    return try {
        when {
            // `open <dir>` on macOS opens Finder viewing the directory.
            isMac -> {
                ProcessBuilder("open", folder.absolutePath).start()
                true
            }
            // `explorer <dir>` (no /select) opens Explorer viewing the
            // directory. cmd /c start handles quoting for paths with
            // spaces — same reasoning as revealInFileExplorer.
            isWindows -> {
                ProcessBuilder("cmd", "/c", "start", "", folder.absolutePath).start()
                true
            }
            else -> {
                ProcessBuilder("xdg-open", folder.absolutePath).start()
                true
            }
        }
    } catch (_: Throwable) {
        false
    }
}
