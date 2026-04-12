package org.open.file.template.org.open.file.template.store.mongo

import com.mongodb.client.model.BulkWriteOptions
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.ReplaceOneModel
import com.mongodb.client.model.ReplaceOptions
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.bson.types.ObjectId
import org.open.file.shared.mongo.MongoDatabaseProvider
import org.open.file.store.models.StoreType
import org.open.file.template.models.Template
import org.open.file.template.store.TemplateDao
import java.io.IOException

class TemplateMongoDao: TemplateDao {

    private val db = MongoDatabaseProvider.get()
    private val collection = db.getCollection<Template>("templates")

    override val type: StoreType
        get() = StoreType.MONGO

    override fun create(template: Template): Template? {
        val result = runBlocking { collection.insertOne(template) }
        if (result.wasAcknowledged()) {
            return template
        }
        return null
    }

    override fun read(id: String): Template? {
        val result = runBlocking{ collection.find(eq("_id", ObjectId(id))).firstOrNull() }
        return result
    }

    override fun update(template: Template, upsert: Boolean) {
        val options = ReplaceOptions().upsert(upsert)
        val result = runBlocking {
            collection.replaceOne(
                eq("_id", ObjectId(template.id.toString())),
                template,
                options
            )
        }
        if (!result.wasAcknowledged()) {
            throw IOException("Couldn't write to mongo!")
        }
    }

    override fun update(templateList: List<Template>, upsert: Boolean) {
        val options = ReplaceOptions().upsert(upsert)
        val operations = templateList.map { template ->
            ReplaceOneModel(
                eq("_id", template.id),
                template,
                options
            )
        }
        val result = runBlocking { collection.bulkWrite(operations, BulkWriteOptions().ordered(false)) }
        if (!result.wasAcknowledged()) {
            throw IOException("Couldn't write to mongo!")
        }
    }

    override fun delete(template: Template): Boolean {
        if (!ObjectId.isValid(template.id.toString())) return false
        val result = runBlocking { collection.deleteOne(eq("_id", ObjectId(template.id.toString()))) }
        return result.deletedCount > 0
    }

    override fun list(): List<Template> {
        val result = runBlocking { collection.find<Template>().limit(100).toList() }
        return result
    }

}