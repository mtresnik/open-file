package org.open.file.ui.data

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import org.open.file.utils.FileSystemUtils
import java.io.File
import java.util.Properties

/**
 * Persists the app window's size / position / placement across
 * restarts. File: `~/.open-file/ui/window.properties`
 *
 *   width     = 1200
 *   height    = 780
 *   x         = 220
 *   y         = 140
 *   placement = Floating | Maximized | Fullscreen
 *
 * All values are dp integers (rounded on save) because
 * Properties can only carry strings and we don't need sub-pixel
 * precision for window placement — the OS will snap to its own
 * pixel grid anyway.
 *
 * Missing values at load time mean "no preference" and the caller
 * falls back to a sensible default (platform-chosen position,
 * 1200×780 size, Floating placement).
 */
class WindowPreferences(
    private val prefsFile: File = FileSystemUtils.home("ui/window.properties"),
) {
    private val lock = Any()

    data class State(
        val width: Dp?,
        val height: Dp?,
        val x: Dp?,
        val y: Dp?,
        val placement: WindowPlacement?,
    )

    fun load(): State = synchronized(lock) {
        if (!prefsFile.exists()) return@synchronized State(null, null, null, null, null)
        val props = Properties()
        prefsFile.inputStream().use { props.load(it) }
        State(
            width = props.getProperty(KEY_WIDTH)?.toDoubleOrNull()?.dp,
            height = props.getProperty(KEY_HEIGHT)?.toDoubleOrNull()?.dp,
            x = props.getProperty(KEY_X)?.toDoubleOrNull()?.dp,
            y = props.getProperty(KEY_Y)?.toDoubleOrNull()?.dp,
            placement = props.getProperty(KEY_PLACEMENT)?.let { name ->
                runCatching { WindowPlacement.valueOf(name) }.getOrNull()
            },
        )
    }

    fun save(
        width: Dp?,
        height: Dp?,
        x: Dp?,
        y: Dp?,
        placement: WindowPlacement?,
    ) = synchronized(lock) {
        val props = Properties()
        // Read-existing-then-overwrite so callers can save just a
        // subset (e.g. only placement) without clobbering everything.
        if (prefsFile.exists()) prefsFile.inputStream().use { props.load(it) }
        width?.let { props.setProperty(KEY_WIDTH, it.value.toString()) }
        height?.let { props.setProperty(KEY_HEIGHT, it.value.toString()) }
        x?.let { props.setProperty(KEY_X, it.value.toString()) }
        y?.let { props.setProperty(KEY_Y, it.value.toString()) }
        placement?.let { props.setProperty(KEY_PLACEMENT, it.name) }
        prefsFile.parentFile?.mkdirs()
        prefsFile.outputStream().use { props.store(it, "open-file window state") }
    }

    private fun Double.dp(): Dp = this.dp

    // Dp is a value class wrapping a Float; Properties stored as
    // doubles rehydrates cleanly through `.toDoubleOrNull()`.
    private val Double.dp: Dp get() = this.toFloat().dp

    companion object {
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val KEY_PLACEMENT = "placement"
    }
}
