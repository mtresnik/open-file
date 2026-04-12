//@file:Suppress("UNCHECKED_CAST")
package org.open.file.template.org.open.file.template.store.adapters

import app.cash.sqldelight.ColumnAdapter
import org.open.file.template.models.directory.DirectoryTemplate
import org.open.file.shared.sql.adapters.FileAdapter
import java.io.File

object TemplateTargetAdapter : ColumnAdapter<Any, String> {
    override fun decode(databaseValue: String): Any {
        val asFile = File(databaseValue)
        val isFile = asFile.exists()
        if (isFile) return FileAdapter.decode(databaseValue)

        return databaseValue
    }

    override fun encode(value: Any): String {
        return when (value) {
            is File -> {
                FileAdapter.encode(value)
            }
            else -> value.toString()
        }
    }

    fun <T: Any> getAdapterByType(type: String): ColumnAdapter<Any, String> {
        return when (type) {
            DirectoryTemplate.TYPE -> {
                FileAdapter
            }

            else -> this
        } as ColumnAdapter<Any, String>
    }
}