package org.open.file.backup.models

import java.util.UUID
import kotlin.time.Instant

/**
 * A compressed backup of a directory tree.
 *
 * Deliberately mirrors [org.open.file.snapshot.models.Snapshot] so the UI,
 * services and DAOs can follow the same Saved / Unsaved split:
 *
 *  - [rootPath]: the source directory that was archived.
 *  - [destinationPath]: absolute path to the compressed archive file on disk.
 *  - [targetDirectory]: absolute path to the parent directory the archive
 *    was written into. Same as `File(destinationPath).parent` for the default
 *    flow, but stored explicitly so:
 *      (a) the UI can show "where future backups of this path will land"
 *          without re-parsing the archive filename, and
 *      (b) the "new backup from same source" / scheduled paths can re-use
 *          the user's chosen target directory without touching the old row.
 *    Nullable for backwards-compat with rows written before this field
 *    existed; callers default to the built-in archive dir in that case.
 *  - [compression]: how [destinationPath] was encoded — see [CompressionType].
 *  - [originalSize] / [archivedSize]: bytes before and after compression, used
 *    by the UI to show the compression ratio without having to re-stat anything.
 *  - [snapshotId]: optional link to a [org.open.file.snapshot.models.Snapshot]
 *    captured at archive time so the user can see what was included.
 */
interface Backup {
    val rootPath: String
    val destinationPath: String
    val targetDirectory: String?
    val createdAt: Instant
    val compression: CompressionType
    val originalSize: Long
    val archivedSize: Long
    val entryCount: Int
    val snapshotId: String?

    /**
     * Whether the archive includes hidden files / dotdirs. Persisted
     * per-row so replays ("new backup from same source") can honour
     * the original creation-time toggle. Defaults true for rows
     * written before this field existed — pre-toggle behaviour was
     * effectively "include everything", so true is the right back-
     * compat value.
     */
    val includeHidden: Boolean

    fun toSaved(id: String = UUID.randomUUID().toString()): SavedBackup = SavedBackup(
        id = id,
        rootPath = rootPath,
        destinationPath = destinationPath,
        targetDirectory = targetDirectory,
        createdAt = createdAt,
        compression = compression,
        originalSize = originalSize,
        archivedSize = archivedSize,
        entryCount = entryCount,
        snapshotId = snapshotId,
        includeHidden = includeHidden,
    )
}

/** Transient backup — no id yet. Produced by the archiver before persistence. */
data class UnsavedBackup(
    override val rootPath: String,
    override val destinationPath: String,
    override val targetDirectory: String? = null,
    override val createdAt: Instant,
    override val compression: CompressionType = CompressionType.ZIP,
    override val originalSize: Long,
    override val archivedSize: Long,
    override val entryCount: Int,
    override val snapshotId: String? = null,
    override val includeHidden: Boolean = true,
) : Backup

/** Persisted backup — has a stable id assigned by the DAO layer. */
data class SavedBackup(
    val id: String,
    override val rootPath: String,
    override val destinationPath: String,
    override val targetDirectory: String? = null,
    override val createdAt: Instant,
    override val compression: CompressionType,
    override val originalSize: Long,
    override val archivedSize: Long,
    override val entryCount: Int,
    override val snapshotId: String?,
    override val includeHidden: Boolean = true,
) : Backup {
    override fun toSaved(id: String): SavedBackup = this
}
