package org.open.file.shared.mongo

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.open.file.shared.docker.Docker
import org.open.file.shared.mongo.localhost.LocalhostMongoLauncher
import org.open.file.utils.isLocalhost

object MongoDatabaseProvider {

    fun get() : MongoDatabase {
        if (isLocalhost(MongoConfig.mongoHost)) {
            val docker = Docker.client()
            LocalhostMongoLauncher.ensureMongoRunning(docker)
        }
        val client = MongoClient.create(MongoConfig.mongoUri)
        val db = client.getDatabase(MongoConfig.mongoDatabase)
        return db
    }

}