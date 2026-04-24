package org.open.file.ui.data

import org.open.file.utils.FileSystemUtils
import java.io.File
import java.util.Properties
import java.util.UUID

/**
 * UI-only storage for each template's chosen icon.
 *
 * The domain [org.open.file.template.models.Template] doesn't carry an icon
 * field and extending the SQL schema for a purely-cosmetic attribute would be
 * overkill, so we persist the mapping on disk under the UI's own control at
 * `~/.open-file/ui/template-icons.properties`:
 *
 *   template-id=<iconRef>
 *
 * `iconRef` is either a built-in key ("kotlin", "react", …, "generic") or an
 * absolute path to a user-uploaded image file copied into
 * `~/.open-file/ui/icons/`.
 *
 * This class is thread-safe at the file-write level (each mutation rewrites
 * the whole Properties file under a `synchronized` block) and is intended
 * to be called from the UI's IO dispatcher — read/write of a tiny Properties
 * file is cheap but still shouldn't happen on the EDT.
 */
class IconPreferences(
    private val prefsFile: File = FileSystemUtils.home("ui/template-icons.properties"),
    private val iconDir: File = FileSystemUtils.home("ui/icons"),
) {
    private val lock = Any()

    init {
        prefsFile.parentFile?.mkdirs()
        iconDir.mkdirs()
    }

    /** Icon ref for [templateId], or `null` if none has been set yet. */
    fun get(templateId: String): String? = synchronized(lock) { load().getProperty(templateId) }

    /** Full map keyed by template id. Useful for the initial screen load. */
    fun getAll(): Map<String, String> = synchronized(lock) {
        val props = load()
        props.stringPropertyNames().associateWith { props.getProperty(it) }
    }

    /** Persist [iconRef] against [templateId]. Overwrites any previous entry. */
    fun set(templateId: String, iconRef: String) = synchronized(lock) {
        val props = load()
        props.setProperty(templateId, iconRef)
        save(props)
    }

    /**
     * Remove the icon entry and, if it pointed at an uploaded image in our
     * managed [iconDir], delete that file too. Uploaded files outside [iconDir]
     * (e.g. a user's original PNG) are left alone.
     */
    fun remove(templateId: String) = synchronized(lock) {
        val props = load()
        val existing = props.getProperty(templateId)
        if (existing != null && isManagedIcon(existing)) {
            runCatching { File(existing).delete() }
        }
        props.remove(templateId)
        save(props)
    }

    /**
     * Copy [source] into [iconDir] under a fresh id so multiple templates can
     * reference the same uploaded file independently (or re-uploads don't
     * clobber the original). Returns the new absolute path suitable for
     * passing to [set]. Throws if the source doesn't exist or can't be read.
     */
    fun importUpload(source: File): String {
        require(source.exists() && source.isFile && source.canRead()) {
            "Icon file does not exist or is unreadable: ${source.absolutePath}"
        }
        iconDir.mkdirs()
        val ext = source.extension.lowercase().ifBlank { "png" }
        val dest = File(iconDir, "${UUID.randomUUID()}.$ext")
        source.copyTo(dest, overwrite = false)
        return dest.absolutePath
    }

    /** True if [iconRef] is a filesystem path (vs. a built-in key). */
    fun isFilePath(iconRef: String): Boolean =
        iconRef.contains(File.separatorChar) || iconRef.startsWith("/") || iconRef.contains(":\\")

    private fun isManagedIcon(iconRef: String): Boolean =
        isFilePath(iconRef) && File(iconRef).absoluteFile.startsWith(iconDir.absoluteFile)

    private fun load(): Properties {
        val props = Properties()
        if (prefsFile.exists()) {
            prefsFile.inputStream().use { props.load(it) }
        }
        return props
    }

    private fun save(props: Properties) {
        prefsFile.parentFile?.mkdirs()
        prefsFile.outputStream().use { props.store(it, "open-file UI template icons") }
    }
}
