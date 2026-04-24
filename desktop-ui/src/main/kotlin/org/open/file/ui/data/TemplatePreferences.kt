package org.open.file.ui.data

import org.open.file.utils.FileSystemUtils
import java.io.File
import java.util.Properties

/**
 * Template-tab user preferences — currently just the last output
 * directory used for scaffolding, but the file is generic-named so
 * future template-scoped settings can slot in without breaking the
 * storage contract.
 *
 * File: `~/.open-file/ui/templates.properties`:
 *
 *   last.output.dir = /Users/alice/Projects
 *
 * The value is the *parent* directory the user most recently typed /
 * browsed to on the Generate form — the repo wraps it into
 * `<parent>/<projectName>/` on scaffold, so "last used" here means
 * "where do I keep my projects" rather than the full project path.
 *
 * Thread-safe — every mutation goes through [synchronized].
 */
class TemplatePreferences(
    private val prefsFile: File = FileSystemUtils.home("ui/templates.properties"),
) {
    private val lock = Any()

    /** Last parent directory used for scaffolding; null on first run. */
    fun loadLastOutputDir(): String? = synchronized(lock) {
        val props = load()
        props.getProperty(KEY_LAST_OUTPUT_DIR)?.takeIf { it.isNotBlank() }
    }

    fun setLastOutputDir(path: String) = synchronized(lock) {
        if (path.isBlank()) return@synchronized
        val props = load()
        props.setProperty(KEY_LAST_OUTPUT_DIR, path)
        save(props)
    }

    private fun load(): Properties {
        val props = Properties()
        if (prefsFile.exists()) prefsFile.inputStream().use { props.load(it) }
        return props
    }

    private fun save(props: Properties) {
        prefsFile.parentFile?.mkdirs()
        prefsFile.outputStream().use { props.store(it, "open-file template preferences") }
    }

    companion object {
        private const val KEY_LAST_OUTPUT_DIR = "last.output.dir"
    }
}
