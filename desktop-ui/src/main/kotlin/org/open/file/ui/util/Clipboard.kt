package org.open.file.ui.util

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Copy [text] to the system clipboard. Uses AWT directly so it works
 * on every JVM-supported desktop without pulling in Compose-specific
 * clipboard plumbing — those live in `ClipboardManager` + require a
 * composable scope to access.
 *
 * Returns `true` on success, `false` if the clipboard isn't available
 * (headless mode, sandboxed environments, etc). Callers should toast
 * or swap an inline confirmation on true; false silently.
 */
fun copyToClipboard(text: String): Boolean = try {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
    true
} catch (_: Throwable) {
    false
}
