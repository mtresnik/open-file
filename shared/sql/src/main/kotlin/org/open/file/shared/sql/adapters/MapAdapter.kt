package org.open.file.shared.sql.adapters

import app.cash.sqldelight.ColumnAdapter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

open class MapAdapter<T : Any>: ColumnAdapter<MutableMap<String, T>, ByteArray> {

    override fun decode(databaseValue: ByteArray): MutableMap<String, T> {
        val jsonString = String(databaseValue, StandardCharsets.UTF_8)
        return Json.decodeFromString<MutableMap<String, T>>(jsonString)
    }

    override fun encode(value: MutableMap<String, T>): ByteArray {
        val jsonString = Json.encodeToString(value)
        return jsonString.toByteArray(StandardCharsets.UTF_8)
    }

}