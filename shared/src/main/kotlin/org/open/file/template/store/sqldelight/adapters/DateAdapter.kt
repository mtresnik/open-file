package org.open.file.template.store.sqldelight.adapters

import app.cash.sqldelight.ColumnAdapter
import java.util.Date

object DateAdapter : ColumnAdapter<Date, Long> {
    override fun decode(databaseValue: Long): Date {
        return Date(databaseValue)
    }

    override fun encode(value: Date): Long {
        return value.time
    }
}