package org.open.file.archive

/**
 * Archive container formats supported by [ArchiveExtractor] (and,
 * eventually, ArchiveWriter if we generalise the backup zip writer).
 *
 * Decoupled from `org.open.file.backup.models.CompressionType` — that
 * enum carries backup-domain metadata (filename extension, display
 * label for backup rows); this one is purely about the on-disk
 * container format. Keeps `:shared:archive` free of backup domain
 * types, so future consumers (template scaffolding, export flows)
 * don't drag the backup module onto their classpath.
 *
 * Only ZIP is implemented today — it's the only container the app
 * produces or reads. The enum exists as a seam so TAR, 7Z, etc. can
 * slot in later without rewriting every call site.
 */
enum class ArchiveFormat(val extension: String) {
    ZIP("zip");

    companion object {
        /** Lookup by filename extension (case-insensitive). Null on unknown. */
        fun fromExtension(ext: String): ArchiveFormat? =
            values().firstOrNull { it.extension.equals(ext, ignoreCase = true) }
    }
}
