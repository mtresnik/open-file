package org.open.file.backup

import org.open.file.backup.models.Backup
import org.open.file.backup.store.BackupDaoProvider

/**
 * Thin service wrapping [org.open.file.backup.store.BackupDao]. Same shape as
 * [org.open.file.snapshot.SnapshotService] so the repository and UI layers
 * don't need a new playbook for backups.
 */
class BackupService {

    private val dao = BackupDaoProvider.getDao()

    fun getAll(): List<Backup> = dao.readAll()

    fun getById(id: String): Backup? = dao.read(id)

    fun create(backup: Backup): Backup? = dao.create(backup)

    fun delete(backup: Backup): Boolean = dao.delete(backup)

    fun deleteById(id: String): Boolean = dao.deleteById(id)
}
