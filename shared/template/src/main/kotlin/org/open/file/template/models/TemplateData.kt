package org.open.file.template.models

import java.util.Date

open class TemplateData(
    open val type: String,
    open val name: String,
    open val description: String,
    open var created: Date = Date(),
    open var updated: Date = Date(),
    open var deleted: Boolean = false)