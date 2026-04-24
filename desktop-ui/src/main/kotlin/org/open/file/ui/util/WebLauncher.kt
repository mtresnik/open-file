package org.open.file.ui.util

import java.awt.Desktop
import java.net.URI

/**
 * Open [url] in the user's default browser.
 *
 * Tries `java.awt.Desktop.browse` first (the portable API that works on
 * every JVM-supported desktop) and falls back to platform commands
 * (`open`, `rundll32 url.dll,FileProtocolHandler`, `xdg-open`) for the
 * small number of Linux setups where Desktop integration isn't wired up
 * but `xdg-open` still works.
 *
 * Returns `true` on success. Callers should toast a warning on `false`
 * rather than throwing — a user with no default browser configured
 * shouldn't see an exception.
 */
fun openInBrowser(url: String): Boolean {
    return try {
        val uri = URI(url)
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri)
            true
        } else {
            val os = System.getProperty("os.name").orEmpty().lowercase()
            val cmd = when {
                os.contains("mac") || os.contains("darwin") -> listOf("open", url)
                os.contains("win") -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
                else -> listOf("xdg-open", url)
            }
            ProcessBuilder(cmd).start()
            true
        }
    } catch (_: Throwable) {
        false
    }
}
