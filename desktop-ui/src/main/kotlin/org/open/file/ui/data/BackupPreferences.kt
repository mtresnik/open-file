package org.open.file.ui.data

import org.open.file.utils.FileSystemUtils
import java.io.File
import java.util.Properties

/**
 * UI-only preferences for the Backups tab.
 *
 * Lives at `~/.open-file/ui/backup-preferences.properties`. The file
 * name is generic on purpose — add new toggles by picking a fresh
 * key rather than bumping the filename.
 *
 *   warn.identical         = true | false
 *   default.target.dir     = <absolute path> | (absent)
 *
 * Thread-safe — every mutation goes through [synchronized].
 */
class BackupPreferences(
    private val prefsFile: File = FileSystemUtils.home("ui/backup-preferences.properties"),
) {
    private val lock = Any()

    /**
     * True when the app should prompt before creating a backup whose
     * file count + total size exactly match the most recent backup of
     * the same source directory. Default: true — the prompt is cheap
     * and saves users from accidentally duplicating backups.
     */
    fun isWarnIdenticalEnabled(): Boolean = synchronized(lock) {
        val props = load()
        // Default-on when no explicit value has been stored yet.
        props.getProperty(KEY_WARN_IDENTICAL)?.toBooleanStrictOrNull() ?: true
    }

    fun setWarnIdentical(enabled: Boolean) = synchronized(lock) {
        val props = load()
        props.setProperty(KEY_WARN_IDENTICAL, enabled.toString())
        save(props)
    }

    /**
     * Absolute path the Create Backup dialog pre-fills into the
     * optional target-directory field. `null` / empty = no pref set;
     * the dialog falls back to [PathHints.defaultBackupTargetDir]
     * placeholder and the archiver writes to its own default.
     *
     * Persisted as plain text (no quoting) — Properties handles the
     * escaping. Callers should feed absolute paths; we don't resolve
     * relatives here.
     */
    fun getDefaultTargetDirectory(): String? = synchronized(lock) {
        val props = load()
        props.getProperty(KEY_DEFAULT_TARGET_DIR)?.takeIf { it.isNotBlank() }
    }

    /**
     * Persist (or clear, when [path] is null / blank) the default
     * target directory. Pairs with [getDefaultTargetDirectory].
     */
    fun setDefaultTargetDirectory(path: String?) = synchronized(lock) {
        val props = load()
        if (path.isNullOrBlank()) {
            props.remove(KEY_DEFAULT_TARGET_DIR)
        } else {
            props.setProperty(KEY_DEFAULT_TARGET_DIR, path)
        }
        save(props)
    }

    private fun load(): Properties {
        val props = Properties()
        if (prefsFile.exists()) prefsFile.inputStream().use { props.load(it) }
        return props
    }

    private fun save(props: Properties) {
        prefsFile.parentFile?.mkdirs()
        prefsFile.outputStream().use { props.store(it, "open-file backup preferences") }
    }

    companion object {
        private const val KEY_WARN_IDENTICAL = "warn.identical"
        private const val KEY_DEFAULT_TARGET_DIR = "default.target.dir"
    }
}
