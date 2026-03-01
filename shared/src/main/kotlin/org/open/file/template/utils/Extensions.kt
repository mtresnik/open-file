package org.open.file.template.utils

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Options

operator fun CommandLine.get(option: String): String? {
    return runCatching { getOptionValue(option) }.getOrNull()
}

operator fun CommandLine.contains(value: String): Boolean {
    return this.hasOption(value) || this.options.any { option ->
        option.longOpt == value || option.opt == value
    }
}

operator fun CommandLine.contains(value: Char): Boolean {
    return this.hasOption(value)
}

operator fun Options.contains(value: String): Boolean {
    return this.hasOption(value) || this.hasShortOption(value) || this.hasLongOption(value)
}