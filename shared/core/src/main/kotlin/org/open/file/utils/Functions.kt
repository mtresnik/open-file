package org.open.file.utils

import java.io.File

public inline fun requireNotBlank(value: String): String =
    requireNotBlank(value) { "Required value was blank." }

public inline fun requireNotBlank(value: String, lazyMessage: () -> Any): String {
    if (value.isBlank()) {
        val message = lazyMessage()
        throw IllegalArgumentException(message.toString())
    } else {
        return value
    }
}

public operator fun File.contains(sub: File): Boolean {
    if (!sub.exists()) return false
    return this.isDirectory && sub.absolutePath.startsWith(this.absolutePath)
}

public fun Array<out String?>.filterNotBlank(): List<String> {
    return this.filterNotNull().filter(String::isNotBlank)
}