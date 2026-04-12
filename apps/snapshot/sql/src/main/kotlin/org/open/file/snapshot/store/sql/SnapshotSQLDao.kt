package org.open.file.snapshot.store.sql

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.open.file.shared.sql.adapters.AnyMapAdapter
import org.open.file.shared.sql.adapters.DateAdapter
import org.open.file.shared.sql.adapters.FileAdapter
import org.open.file.shared.sql.adapters.UUIDAdapter
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
    private val databaseUrl = "jdbc:sqlite:file://${snapshotsDatabase.absolutePath}"
    val driver: SqlDriver = JdbcSqliteDriver(databaseUrl, Properties(), Database.Schema)

    override val type: StoreType
        get() = StoreType.SQL

    private fun getDatabase(): Database {
        val adapter = Snapshots.Adapter(
            idAdapter       = UUIDAdapter,
            createdAdapter  = DateAdapter,
            updatedAdapter  = DateAdapter,
            propertiesAdapter = AnyMapAdapter,
            targetAdapter = FileAdapter
        )
        return Database(driver, adapter)
    }

    fun list(): List<Snapshot> {
        val database = getDatabase()
        val snapshots : List<Snapshots> = database.snapshotQueries.selectAll().executeAsList()
        return snapshots.toModel()
    }

    override fun create(snapshot: Snapshot): Snapshot {
        val database = getDatabase()
        snapshot.created = Date()
        snapshot.updated = Date()
        database.snapshotQueries.insertFullsnapshotObject(snapshot.fromModel())
        return snapshot
    }

    override fun read(id: String): Snapshot? {
        val database = getDatabase()
        val databaseObject = database.snapshotQueries.findById(UUID.fromString(id)).executeAsOneOrNull()
        return databaseObject?.toModel()
    }

    override fun update(snapshot: Snapshot, upsert: Boolean) {
        val database = getDatabase()
        database.snapshotQueries.insertFullsnapshotObject(snapshot.fromModel())
    }

    override fun update(snapshotList: List<Snapshot>, upsert: Boolean) {
        val database = getDatabase()
        snapshotList.forEach { snapshot ->
            database.snapshotQueries.insertFullsnapshotObject(snapshot.fromModel())
        }
    }

    override fun delete(snapshot: Snapshot) : Boolean{
        val database = getDatabase()
        database.snapshotQueries.deleteById(snapshot.id)
        return true
    }

    fun findById(id: UUID): Snapshot? {
        val database = getDatabase()
        val found = database.snapshotQueries.findById(id).executeAsOneOrNull()
        return found?.toModel()
    }

}