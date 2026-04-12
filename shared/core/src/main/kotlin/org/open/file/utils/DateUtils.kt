package org.open.file.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {

    private const val DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss z"

    fun Date.format(): String {
        val dateFormatter = SimpleDateFormat(DEFAULT_PATTERN)
        return dateFormatter.format(this)
    }

}