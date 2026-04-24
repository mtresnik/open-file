package org.open.file.backup.store

import org.open.file.backup.models.Backup
import org.open.file.store.models.StoreType

/**
 * Persistence contract for backups. Mirrors
 * [org.open.file.snapshot.store.SnapshotDao] one-for-one so the two domains
 * can share the same UI and repository patterns.
 *
 * Implementations are discovered by [BackupDaoProvider] via `ServiceLoader`,
 * so each backend module drops its impl name into
 * `META-INF/services/org.open.file.backup.store.BackupDao`.
 */
interface BackupDao {
    val type: StoreType

    fun create(backup: Backup): Backup?
    fun read(id: String): Backup?
    fun readAll(): List<Backup>
    fun update(backup: Backup, upsert: Boolean = true)
    fun update(backupList: List<Backup>, upsert: Boolean = true)
    fun delete(backup: Backup): Boolean
    fun deleteById(id: String): Boolean
}
