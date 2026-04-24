package org.open.file.ui.data

import org.open.file.utils.FileSystemUtils
import java.io.File
import java.util.Properties

/**
 * App-level behaviour toggles that don't belong to any specific tab.
 *
 * File: `~/.open-file/ui/app-behavior.properties`:
 *
 *   minimize.to.tray = true | false
 *
 * Kept separate from [BackupPreferences] (tab-scoped) and
 * [ThemePreferences] (appearance) so each prefs file stays focused on
 * its slice — easier to debug and reason about when a property's value
 * is surprising.
 *
 * Thread-safe — every mutation goes through [synchronized].
 */
class AppBehaviorPreferences(
    private val prefsFile: File = FileSystemUtils.home("ui/app-behavior.properties"),
) {
    private val lock = Any()

    /**
     * True when closing the main window hides it to the system tray
     * (scheduler keeps running) rather than ending the process. False
     * flips the close button back to a plain Quit — useful for users
     * who don't want an always-on tray icon or don't rely on
     * scheduled backups firing in the background.
     *
     * Default-on because the app's scheduled-backup story works best
     * with the tray model, and users who want the classic close=quit
     * can flip it off from Settings without any hidden state.
     */
    fun isMinimizeToTrayEnabled(): Boolean = synchronized(lock) {
        val props = load()
        props.getProperty(KEY_MINIMIZE_TO_TRAY)?.toBooleanStrictOrNull() ?: true
    }

    fun setMinimizeToTray(enabled: Boolean) = synchronized(lock) {
        val props = load()
        props.setProperty(KEY_MINIMIZE_TO_TRAY, enabled.toString())
        save(props)
    }

    private fun load(): Properties {
        val props = Properties()
        if (prefsFile.exists()) prefsFile.inputStream().use { props.load(it) }
        return props
    }

    private fun save(props: Properties) {
        prefsFile.parentFile?.mkdirs()
        prefsFile.outputStream().use { props.store(it, "open-file app behavior") }
    }

    companion object {
        private const val KEY_MINIMIZE_TO_TRAY = "minimize.to.tray"
    }
}
