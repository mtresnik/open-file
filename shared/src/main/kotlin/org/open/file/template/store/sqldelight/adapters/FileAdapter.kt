package org.open.file.template.store.sqldelight.adapters

import app.cash.sqldelight.ColumnAdapter
import java.io.File

object FileAdapter : ColumnAdapter<File, String> {

    override fun decode(databaseValue: String): File {
        return File(databaseValue)
    }

    override fun encode(value: File): String {
        return value.absolutePath
    }

}