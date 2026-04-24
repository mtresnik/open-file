package org.open.file.ui.data

import org.open.file.utils.FileSystemUtils
import java.io.File
import java.util.Properties

/**
 * UI-only store for template metadata the domain layer doesn't carry:
 *  - `parent.<id>` → id of another template this one inherits from
 *  - `tool.<id>.<tool>` → user's chosen version (e.g. `1.9.22`) for
 *    that tool on that template
 *
 * Persisted at `~/.open-file/ui/template-relations.properties`. Stored
 * alongside (not inside) the template SQL schema so adding new
 * UI-surfaced attributes doesn't require a DB migration.
 *
 * Thread-safe — every write goes through [synchronized]. Reads are
 * small (Properties re-parse on each call), which is fine for a handful
 * of entries; if the file ever grows past hundreds of templates we can
 * cache in-memory.
 */
class TemplateRelations(
    private val prefsFile: File = FileSystemUtils.home("ui/template-relations.properties"),
) {
    private val lock = Any()

    // ──────────────────────────────────────────────
    // Parent relations
    // ──────────────────────────────────────────────

    fun getParent(templateId: String): String? = synchronized(lock) {
        load().getProperty("$PARENT_KEY_PREFIX$templateId")?.takeIf { it.isNotBlank() }
    }

    fun setParent(templateId: String, parentId: String?) = synchronized(lock) {
        val props = load()
        val key = "$PARENT_KEY_PREFIX$templateId"
        if (parentId.isNullOrBlank()) props.remove(key) else props.setProperty(key, parentId)
        save(props)
    }

    /** Whole parent map, keyed by template id. */
    fun getAllParents(): Map<String, String> = synchronized(lock) {
        val props = load()
        props.stringPropertyNames()
            .filter { it.startsWith(PARENT_KEY_PREFIX) }
            .associate { it.removePrefix(PARENT_KEY_PREFIX) to props.getProperty(it) }
    }

    // ──────────────────────────────────────────────
    // Selected tool versions
    // ──────────────────────────────────────────────

    fun getSelectedVersion(templateId: String, tool: String): String? = synchronized(lock) {
        load().getProperty("$TOOL_KEY_PREFIX$templateId.$tool")?.takeIf { it.isNotBlank() }
    }

    fun getSelectedVersions(templateId: String): Map<String, String> = synchronized(lock) {
        val props = load()
        val prefix = "$TOOL_KEY_PREFIX$templateId."
        props.stringPropertyNames()
            .filter { it.startsWith(prefix) }
            .associate { it.removePrefix(prefix) to props.getProperty(it) }
    }

    fun setSelectedVersion(templateId: String, tool: String, version: String) = synchronized(lock) {
        val props = load()
        props.setProperty("$TOOL_KEY_PREFIX$templateId.$tool", version)
        save(props)
    }

    // ──────────────────────────────────────────────
    // Clean-up
    // ──────────────────────────────────────────────

    /** Drop everything stored for [templateId]. Called from the repository on delete. */
    fun removeAll(templateId: String) = synchronized(lock) {
        val props = load()
        val parentKey = "$PARENT_KEY_PREFIX$templateId"
        val toolPrefix = "$TOOL_KEY_PREFIX$templateId."
        val removed = mutableListOf<String>()
        props.remove(parentKey)
        for (key in props.stringPropertyNames()) {
            if (key.startsWith(toolPrefix)) removed += key
        }
        removed.forEach { props.remove(it) }
        save(props)
    }

    // ──────────────────────────────────────────────
    // Disk I/O
    // ──────────────────────────────────────────────

    private fun load(): Properties {
        val props = Properties()
        if (prefsFile.exists()) prefsFile.inputStream().use { props.load(it) }
        return props
    }

    private fun save(props: Properties) {
        prefsFile.parentFile?.mkdirs()
        prefsFile.outputStream().use { props.store(it, "open-file UI template relations") }
    }

    companion object {
        private const val PARENT_KEY_PREFIX = "parent."
        private const val TOOL_KEY_PREFIX = "tool."
    }
}
