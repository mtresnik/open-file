package org.open.file.backup.models

/**
 * Wire format for a backup archive.
 *
 * Only [ZIP] is implemented for now — it's in `java.util.zip` out of the
 * box, works on every host OS, and is unzip-able with nothing more than the
 * user's default file manager. The enum exists so we can add GZIP_TAR, ZSTD,
 * etc. later without breaking the persisted schema (the string name is what
 * gets stored in the backups SQL table).
 */
enum class CompressionType(val extension: String, val mimeType: String) {
    ZIP(extension = "zip", mimeType = "application/zip"),
    ;

    companion object {
        /** Parse the stored form tolerantly — unknown values collapse to [ZIP] rather than throwing. */
        fun fromName(name: String?): CompressionType =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ZIP
    }
}
