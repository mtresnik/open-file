package org.open.file.backup.store.sql

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.open.file.backup.Database
import org.open.file.backup.models.Backup
import org.open.file.backup.models.SavedBackup
import org.open.file.backup.store.BackupDao
import org.open.file.store.models.StoreType
import org.open.file.utils.FileSystemUtils.home
import java.io.File
import java.util.Properties
import java.util.UUID

/**
 * SQLite-backed [BackupDao]. Structurally a copy of
 * [org.open.file.snapshot.store.sql.SnapshotSQLDao] — single SQLite file
 * under `~/.open-file/backups.db`, plain `jdbc:sqlite:<abs>` URL (no
 * `file://` prefix, to avoid Windows' drive-letter-as-authority pitfall).
 */
class BackupSQLDao : BackupDao {

    private val backupsDatabase: File by lazy { home(BACKUPS_DB_FILE_NAME).apply { parentFile.mkdirs() } }
    // See SnapshotSQLDao: plain absolute path after `jdbc:sqlite:` instead
    // of a `file://` URI, so Windows drive letters don't trip URI parsing.
    private val databaseUrl = "jdbc:sqlite:${backupsDatabase.absolutePath}"
    val driver: SqlDriver = JdbcSqliteDriver(databaseUrl, Properties(), Database.Schema).also {
        // Opportunistic schema bumps for installs that pre-date
        // column additions. SQLite can't do IF NOT EXISTS on ALTER
        // TABLE ADD COLUMN, so we try each and swallow the
        // "duplicate column name" failure — fresh DBs already have
        // the columns from Database.Schema's CREATE TABLE.
        runCatching {
            it.execute(null, "ALTER TABLE backups ADD COLUMN targetDirectory TEXT", 0)
        }
        runCatching {
            it.execute(null, "ALTER TABLE backups ADD COLUMN includeHidden INTEGER NOT NULL DEFAULT 1", 0)
        }
    }

    override val type: StoreType
        get() = StoreType.SQL

    private fun getDatabase(): Database = Database(driver)

    override fun create(backup: Backup): Backup? {
        val database = getDatabase()
        // Always materialise a stable id on insert. Returning the SavedBackup
        // means callers (and the UI repo) see the same id that got persisted
        // — unlike SnapshotSQLDao, which historically returned the input.
        val saved = backup as? SavedBackup ?: backup.toSaved(UUID.randomUUID().toString())
        database.backupsQueries.insertFullBackupObject(saved.fromModel())
        return saved
    }

    override fun read(id: String): Backup? {
        val database = getDatabase()
        return database.backupsQueries.findById(id).executeAsOneOrNull()?.toModel()
    }

    override fun readAll(): List<Backup> {
        val database = getDatabase()
        return database.backupsQueries.selectAll().executeAsList().toModelList()
    }

    override fun update(backup: Backup, upsert: Boolean) {
        val database = getDatabase()
        // INSERT OR REPLACE makes upsert semantics free.
        database.backupsQueries.insertFullBackupObject(backup.fromModel())
    }

    override fun update(backupList: List<Backup>, upsert: Boolean) {
        val database = getDatabase()
        backupList.forEach { database.backupsQueries.insertFullBackupObject(it.fromModel()) }
    }

    override fun delete(backup: Backup): Boolean {
        val database = getDatabase()
        val id = (backup as? SavedBackup)?.id ?: backup.toSaved().id
        database.backupsQueries.deleteById(id)
        return true
    }

    override fun deleteById(id: String): Boolean {
        val database = getDatabase()
        database.backupsQueries.deleteById(id)
        return true
    }
}
