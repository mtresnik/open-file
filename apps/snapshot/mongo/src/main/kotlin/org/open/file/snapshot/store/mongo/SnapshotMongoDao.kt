package org.open.file.snapshot.store.mongo

import com.mongodb.client.model.BulkWriteOptions
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.ReplaceOneModel
import com.mongodb.client.model.ReplaceOptions
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.bson.types.ObjectId
import org.open.file.shared.mongo.MongoDatabaseProvider
import org.open.file.snapshot.models.Snapshot
import org.open.file.snapshot.store.SnapshotDao
import org.open.file.store.models.StoreType
import java.io.IOException

class SnapshotMongoDao : SnapshotDao {

    private val db = MongoDatabaseProvider.get()
    private val collection = db.getCollection<Snapshot>("snapshots")

    override val type: StoreType
        get() = StoreType.MONGO

    override fun create(snapshot: Snapshot): Snapshot? {
        val result = runBlocking { collection.insertOne(snapshot) }
        if (result.wasAcknowledged()) {
            return snapshot
        }
        return null
    }

    override fun read(id: String): Snapshot? {
        val result = runBlocking{ collection.find(eq("_id", ObjectId(id))).firstOrNull() }
        return result
    }

    override fun update(snapshot: Snapshot, upsert: Boolean) {
        val options = ReplaceOptions().upsert(upsert)
        val result = runBlocking {
            collection.replaceOne(
                eq("_id", ObjectId(snapshot.id.toString())),
                snapshot,
                options
            )
        }
        if (!result.wasAcknowledged()) {
            throw IOException("Couldn't write to mongo!")
        }
    }

    override fun update(snapshotList: List<Snapshot>, upsert: Boolean) {
        val options = ReplaceOptions().upsert(upsert)
        val operations = snapshotList.map { snapshot ->
            ReplaceOneModel(
                eq("_id", snapshot.id),
                snapshot,
                options
            )
        }
        val result = runBlocking { collection.bulkWrite(operations, BulkWriteOptions().ordered(false)) }
        if (!result.wasAcknowledged()) {
            throw IOException("Couldn't write to mongo!")
        }
    }

    override fun delete(snapshot: Snapshot) : Boolean {
        if (!ObjectId.isValid(snapshot.id.toString())) return false
        val result = runBlocking { collection.deleteOne(eq("_id", ObjectId(snapshot.id.toString()))) }
        return result.deletedCount > 0
    }

}