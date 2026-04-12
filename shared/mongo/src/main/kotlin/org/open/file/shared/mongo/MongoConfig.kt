package org.open.file.shared.mongo

import java.util.Properties

object MongoConfig {
    private val props = Properties().apply {
        // Load from classpath
        MongoConfig::class.java.getResourceAsStream("/mongo.properties")
            ?.use { load(it) }
    }

    // System properties (from -P gradle params) override the file
    val mongoHost: String = System.getProperty("mongo.host") ?: props.getProperty("mongo.host", "localhost")
    val mongoPort: Int = (System.getProperty("mongo.port") ?: props.getProperty("mongo.port", "27017")).toInt()
    val mongoDatabase: String = System.getProperty("mongo.database") ?: props.getProperty("mongo.database", "mydb")

    val mongoUri: String get() = System.getProperty("mongo.uri")
        ?: props.getProperty("mongo.uri", null)
        ?: "mongodb://$mongoHost:$mongoPort/$mongoDatabase"

}