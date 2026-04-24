package org.open.file.ui.data

import org.open.file.utils.FileSystemUtils
import java.io.File
import java.util.Properties

/**
 * Persists the user's chosen UI language. Stored at
 * `~/.open-file/ui/language.properties`:
 *
 *   code=en
 *
 * Paired with the in-memory [org.open.file.ui.i18n.LocalStrings]
 * CompositionLocal — this class only reads/writes the code; the UI layer
 * looks up the matching [org.open.file.ui.i18n.Locale] and updates the
 * provider when the user picks something different.
 */
class LanguagePreferences(
    private val prefsFile: File = FileSystemUtils.home("ui/language.properties"),
) {
    private val lock = Any()

    fun load(): String? = synchronized(lock) {
        if (!prefsFile.exists()) return null
        val props = Properties()
        prefsFile.inputStream().use { props.load(it) }
        props.getProperty(CODE_KEY)
    }

    fun save(code: String) = synchronized(lock) {
        val props = Properties()
        if (prefsFile.exists()) prefsFile.inputStream().use { props.load(it) }
        props.setProperty(CODE_KEY, code)
        prefsFile.parentFile?.mkdirs()
        prefsFile.outputStream().use { props.store(it, "open-file UI language") }
    }

    companion object {
        private const val CODE_KEY = "code"
    }
}
