package org.open.file.ui.util

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Short grace-period lock on dialog dismiss, set whenever a native
 * file picker closes. Some window managers deliver the click that
 * raced with the picker closing to the Compose dialog *underneath*,
 * which would otherwise dismiss the owning AppDialog a frame after
 * the user picked a file.
 *
 * [AppDialog] consults [canDismissNow] inside its onDismissRequest
 * handler — if the lock is still active, the dismiss request is
 * swallowed, and the user has to deliberately click again (or press
 * ESC) to close the dialog. 500ms is enough to cover the OS's
 * close-animation + the one-frame race without feeling laggy.
 *
 * Module-global singleton because (a) the picker functions don't
 * currently have access to a Compose scope and (b) the lock is
 * process-wide: only one native picker can be open at a time.
 */
object DialogDismissLock {
    /** Default grace period — 500ms per the UX request. */
    const val DEFAULT_GRACE_MS: Long = 500

    @Volatile
    private var lockedUntilMs: Long = 0

    /** Extend the lock to end [graceMs] from now. Safe to call from any thread. */
    fun lockFor(graceMs: Long = DEFAULT_GRACE_MS) {
        lockedUntilMs = System.currentTimeMillis() + graceMs
    }

    /** True if enough time has passed since the last picker that a dismiss is safe. */
    fun canDismissNow(): Boolean = System.currentTimeMillis() >= lockedUntilMs
}

/**
 * Opens the host OS's native directory picker and returns the selected absolute
 * path, or `null` if the user cancelled.
 *
 * Platform behaviour:
 *  - macOS: uses AWT's [FileDialog] with `apple.awt.fileDialogForDirectories=true`,
 *    which produces the real Finder-style chooser.
 *  - Windows / Linux: uses Swing's [JFileChooser] in
 *    [JFileChooser.DIRECTORIES_ONLY] mode under the system look-and-feel so the
 *    dialog blends with the host UI.
 *
 * This must be invoked from the AWT EDT. When called from a Compose click
 * handler (which already runs on the EDT) that's automatic; otherwise the call
 * is marshalled onto the EDT via [SwingUtilities.invokeAndWait].
 */
fun pickDirectory(
    title: String = "Choose Directory",
    startDirectory: String? = null,
): String? {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val isMac = os.contains("mac") || os.contains("darwin")

    val task = { if (isMac) pickDirectoryMac(title, startDirectory) else pickDirectorySwing(title, startDirectory) }

    val picked = if (SwingUtilities.isEventDispatchThread()) {
        task()
    } else {
        val result = arrayOfNulls<String>(1)
        SwingUtilities.invokeAndWait { result[0] = task() }
        result[0]
    }
    // Grace period so a click that races with the native picker
    // closing can't also dismiss the owning Compose dialog. Applied
    // on both cancel and pick paths — the race looks the same either
    // way from the WM's perspective.
    DialogDismissLock.lockFor()
    return picked
}

private fun pickDirectoryMac(title: String, startDirectory: String?): String? {
    val propKey = "apple.awt.fileDialogForDirectories"
    val previous = System.getProperty(propKey)
    return try {
        System.setProperty(propKey, "true")
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
            startDirectory?.let { directory = it }
            isMultipleMode = false
        }
        dialog.isVisible = true
        val dir = dialog.directory
        val file = dialog.file
        if (dir != null && file != null) File(dir, file).absolutePath else null
    } finally {
        if (previous == null) System.clearProperty(propKey)
        else System.setProperty(propKey, previous)
    }
}

private fun pickDirectorySwing(title: String, startDirectory: String?): String? {
    try {
        // Match the host window chrome instead of Swing's cross-platform L&F.
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (_: Throwable) {
        // Non-fatal: fall through with whatever L&F is active.
    }
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isMultiSelectionEnabled = false
        startDirectory?.takeIf { it.isNotBlank() }?.let { path ->
            val f = File(path)
            if (f.isDirectory) currentDirectory = f
        }
    }
    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.absolutePath else null
}

/**
 * Opens the host OS's native file picker restricted to image files and
 * returns the selected absolute path, or `null` if the user cancelled.
 *
 * [extensions] is a list of lowercase extensions without the dot (e.g.
 * `listOf("png", "ico", "jpg")`). On macOS the AWT [FileDialog] applies them
 * via a [FilenameFilter]; on other platforms Swing's
 * [FileNameExtensionFilter] does the same job in the [JFileChooser].
 */
fun pickImageFile(
    title: String = "Choose Image",
    extensions: List<String> = listOf("png", "ico", "jpg", "jpeg", "gif", "bmp"),
): String? {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val isMac = os.contains("mac") || os.contains("darwin")

    val task = { if (isMac) pickImageFileMac(title, extensions) else pickImageFileSwing(title, extensions) }

    val picked = if (SwingUtilities.isEventDispatchThread()) {
        task()
    } else {
        val result = arrayOfNulls<String>(1)
        SwingUtilities.invokeAndWait { result[0] = task() }
        result[0]
    }
    // See pickDirectory — grace period prevents the dismiss race.
    DialogDismissLock.lockFor()
    return picked
}

private fun pickImageFileMac(title: String, extensions: List<String>): String? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
        isMultipleMode = false
        // FileDialog's filter is advisory on macOS (Finder still shows other
        // files greyed out), but it keeps parity with the Swing branch and
        // stops the user from accidentally picking an arbitrary file.
        filenameFilter = FilenameFilter { _, name ->
            val lower = name.lowercase()
            extensions.any { lower.endsWith(".$it") }
        }
    }
    dialog.isVisible = true
    val dir = dialog.directory
    val file = dialog.file
    return if (dir != null && file != null) File(dir, file).absolutePath else null
}

private fun pickImageFileSwing(title: String, extensions: List<String>): String? {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (_: Throwable) {
        // Non-fatal.
    }
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.FILES_ONLY
        isMultiSelectionEnabled = false
        isAcceptAllFileFilterUsed = false
        fileFilter = FileNameExtensionFilter(
            "Images (${extensions.joinToString(", ") { "*.$it" }})",
            *extensions.toTypedArray(),
        )
    }
    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.absolutePath else null
}
