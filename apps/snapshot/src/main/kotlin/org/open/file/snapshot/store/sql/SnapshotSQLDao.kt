package org.open.file.snapshot.store.sql

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.open.file.snapshot.Database
import org.open.file.snapshot.Snapshots
import org.open.file.snapshot.models.Snapshot
import org.open.file.snapshot.store.SnapshotDao
import org.open.file.store.models.StoreType
import org.open.file.utils.FileSystemUtils.home
import java.io.File
import java.util.*

class SnapshotSQLDao : SnapshotDao {

    private val snapshotsDatabase: File by lazy { home(SNAPSHOTS_DB_FILE_NAME).apply { parentFile.mkdirs() } }
    // Don't wrap in a `file://` URI: on Windows `File.absolutePath` starts
    // with `C:\...`, which makes the driver parse `C:` as the URI authority
    // and fail with "invalid uri authority". The xerial driver accepts a
    // plain absolute path after `jdbc:sqlite:` on every platform.
    private val databaseUrl = "jdbc:sqlite:${snapshotsDatabase.absolutePath}"
    val driver: SqlDriver = JdbcSqliteDriver(databaseUrl, Properties(), Database.Schema)

    override val type: StoreType
        get() = StoreType.SQL

    private fun getDatabase(): Database {
        return Database(driver)
    }

    fun list(): List<Snapshot> {
        val database = getDatabase()
        val snapshots : List<Snapshots> = database.snapshotsQueries.selectAll().executeAsList()
        return snapshots.toModelList()
    }

    override fun readAll(): List<Snapshot> {
        val database = getDatabase()
        val snapshots : List<Snapshots> = database.snapshotsQueries.selectAll().executeAsList()
        return snapshots.toModelList()
    }

    override fun create(snapshot: Snapshot): Snapshot {
        val database = getDatabase()
        val toSave = snapshot.toSaved()
        database.snapshotsQueries.insertFullsnapshotObject(toSave.fromModel())
        return snapshot
    }

    override fun read(id: String): Snapshot? {
        val database = getDatabase()
        val databaseObject = database.snapshotsQueries.findById(id).executeAsOneOrNull()
        return databaseObject?.toModel()
    }

    override fun update(snapshot: Snapshot, upsert: Boolean) {
        val database = getDatabase()
        val toSave = snapshot.toSaved()
        database.snapshotsQueries.insertFullsnapshotObject(toSave.fromModel())
    }

    override fun update(snapshotList: List<Snapshot>, upsert: Boolean) {
        val database = getDatabase()
        snapshotList.forEach { snapshot ->
            database.snapshotsQueries.insertFullsnapshotObject(snapshot.toSaved().fromModel())
        }
    }

    override fun delete(snapshot: Snapshot) : Boolean{
        val database = getDatabase()
        val toSave = snapshot.toSaved()
        database.snapshotsQueries.deleteById(toSave.id)
        return true
    }

    fun findById(id: UUID): Snapshot? {
        val database = getDatabase()
        val found = database.snapshotsQueries.findById(id.toString()).executeAsOneOrNull()
        return found?.toModel()
    }

}