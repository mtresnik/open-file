package org.open.file.snapshot.store.mongo

import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.kotlinx.KotlinSerializerCodecProvider
import org.open.file.shared.mongo.MongoDatabaseProvider
import org.open.file.snapshot.store.NodeDao
import org.open.file.snapshot.store.domain.SnapshotNode

class NodeMongoDao: NodeDao {

    private val db = MongoDatabaseProvider.get()
    private val codecRegistry = CodecRegistries.fromRegistries(
        CodecRegistries.fromProviders(KotlinSerializerCodecProvider()),
        db.codecRegistry
    )
    private val collection: MongoCollection<SnapshotNode> = db
        .withCodecRegistry(codecRegistry)
        .getCollection<SnapshotNode>("nodes")

    override fun insert(node: SnapshotNode) = runBlocking {
        collection.insertOne(node)
        Unit
    }

    override fun insertAll(nodes: List<SnapshotNode>) = runBlocking {
        collection.insertMany(nodes)
        Unit
    }

    override fun getTree(snapshotId: String): SnapshotNode? = runBlocking {
        collection.find(eq("snapshotId", snapshotId))
            .firstOrNull()
    }

    override fun getChildren(parentId: String): List<SnapshotNode> = runBlocking {
        collection.find(eq("parentId", parentId))
            .toList()
    }

    override fun deleteBySnapshotId(snapshotId: String) = runBlocking {
        collection.deleteMany(eq("snapshotId", snapshotId))
        Unit
    }

    override fun readAll(): List<SnapshotNode> {
        val result = runBlocking { collection.find<SnapshotNode>().limit(100).toList() }
        return result
    }

}