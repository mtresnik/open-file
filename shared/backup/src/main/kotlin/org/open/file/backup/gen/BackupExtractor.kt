package org.open.file.backup.gen

import org.open.file.archive.ArchiveExtractor
import org.open.file.archive.ArchiveFormat
import org.open.file.backup.models.CompressionType
import java.io.File

/**
 * Legacy facade — all logic moved to [ArchiveExtractor] in
 * `:shared:archive` so template scaffolding (and future export flows)
 * can reuse the zip pipeline without depending on the backup domain.
 *
 * Kept as a thin delegating shim for backwards compatibility with any
 * caller that hasn't migrated yet. New code should use
 * [ArchiveExtractor] directly and map the backup-domain
 * [CompressionType] to [ArchiveFormat] at the boundary.
 */
@Deprecated(
    message = "Use ArchiveExtractor from :shared:archive directly.",
    replaceWith = ReplaceWith(
        "ArchiveExtractor.extract(archive, destination, format)",
        "org.open.file.archive.ArchiveExtractor",
        "org.open.file.archive.ArchiveFormat",
    ),
)
object BackupExtractor {

    /**
     * @see ArchiveExtractor.extract
     */
    fun extract(
        archive: File,
        destination: File,
        compression: CompressionType = CompressionType.ZIP,
    ): ExtractResult {
        val format = when (compression) {
            CompressionType.ZIP -> ArchiveFormat.ZIP
        }
        val r = ArchiveExtractor.extract(archive, destination, format)
        return ExtractResult(
            fileCount = r.fileCount,
            directoryCount = r.directoryCount,
            totalBytes = r.totalBytes,
        )
    }

    /** Local mirror of [ArchiveExtractor.ExtractResult] for shim compat. */
    data class ExtractResult(
        val fileCount: Int,
        val directoryCount: Int,
        val totalBytes: Long,
    )
}
