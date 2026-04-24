package org.open.file.backup.store.sql

import org.open.file.backup.Backups
import org.open.file.backup.models.Backup
import org.open.file.backup.models.CompressionType
import org.open.file.backup.models.SavedBackup
import kotlin.time.Instant

/**
 * Row → domain: reconstruct a [SavedBackup] from the generated [Backups]
 * row type. Compression values are parsed tolerantly — unknown strings
 * fall back to [CompressionType.ZIP] rather than crashing the read path
 * if a future enum variant is rolled back.
 */
fun Backups.toModel(): Backup = SavedBackup(
    id = this.id,
    rootPath = this.rootPath,
    destinationPath = this.destinationPath,
    // Pre-column rows read back as null here — the UI falls through to
    // derive the parent from destinationPath or the built-in default.
    targetDirectory = this.targetDirectory,
    createdAt = Instant.fromEpochMilliseconds(this.createdAt),
    compression = CompressionType.fromName(this.compression),
    originalSize = this.originalSize,
    archivedSize = this.archivedSize,
    entryCount = this.entryCount.toInt(),
    snapshotId = this.snapshotId,
    // SQLite stores booleans as INTEGER 0/1. `!= 0L` handles nullable
    // for rows migrated in via ALTER TABLE that ended up NULL — the
    // column's NOT NULL DEFAULT 1 should prevent this in practice,
    // but the defensive check keeps a bad row from blowing up reads.
    includeHidden = (this.includeHidden != 0L),
)

fun List<Backups>.toModelList(): List<Backup> = this.map(Backups::toModel)

fun Backup.fromModel(assignedId: String? = null): Backups {
    // Inserts coming from UnsavedBackup need a fresh id; SavedBackup already
    // carries one. [assignedId] lets the DAO override either case.
    val id = assignedId ?: (this as? SavedBackup)?.id ?: this.toSaved().id
    return Backups(
        id = id,
        rootPath = this.rootPath,
        destinationPath = this.destinationPath,
        targetDirectory = this.targetDirectory,
        createdAt = this.createdAt.toEpochMilliseconds(),
        compression = this.compression.name,
        originalSize = this.originalSize,
        archivedSize = this.archivedSize,
        entryCount = this.entryCount.toLong(),
        snapshotId = this.snapshotId,
        includeHidden = if (this.includeHidden) 1L else 0L,
    )
}
