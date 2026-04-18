package org.open.file.cli

import org.apache.commons.cli.CommandLine
import org.open.file.utils.contains
import org.open.file.utils.get

val CommandLine.description: String?
    get() = this["description"]

val CommandLine.file: String?
    get() = this["file"]

val CommandLine.help: Boolean
    get() = "help" in this

val CommandLine.list: Boolean
    get() = "list" in this

val CommandLine.new: Boolean
    get() = "new" in this

val CommandLine.name: String?
    get() = this["name"]

val CommandLine.quit: Boolean
    get() = listOf("q", "quit").any { it in this }

val CommandLine.type: String?
    get() = this["type"] ?: this["t"]

val CommandLine.types: Boolean
    get() = "types" in this