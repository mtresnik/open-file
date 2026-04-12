package org.open.file.shared.sql.adapters

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