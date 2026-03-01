package org.open.file.template.utils

import org.slf4j.event.Level
import kotlin.toString

object LoggingUtils {

    var logLevel: Level = Level.INFO
        set(value) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", value.toString())
            field = value
        }

}