package org.open.file.utils

import java.io.File

fun File.lastModifiedHash(): Int {
    return listOf(this.lastModified(), this.length()).hashCode()
}

fun File.equalsOtherLastModified(other : File): Boolean {
    return this.lastModifiedHash() == other.lastModifiedHash()
}