package org.open.file.template.models

import org.open.file.template.models.directory.DirectoryTemplate
import org.open.file.template.models.directory.DirectoryTemplateConfig
import java.io.File
import java.util.Date
import java.util.UUID
import kotlin.properties.Delegates

abstract class Template(
    open var id: UUID,
    open val type: String,
    open val name: String,
    open val description: String,
    open val target: Any,
    open var created: Date,
    open var updated: Date,
    open var deleted: Boolean,
    open val config: TemplateConfig
) {

    class Builder {

        private var id: UUID by Delegates.notNull()
        fun id(id: UUID) = apply { this.id = id }

        private var type: String by Delegates.notNull()
        fun type(type: String) = apply { this.type = type }

        private var name: String by Delegates.notNull()
        fun name(name: String) = apply { this.name = name }

        private var description: String? = null
        fun description(description: String?) = apply { this.description = description }

        private var target: String by Delegates.notNull()
        fun target(target: String) = apply { this.target = target }

        private var created: Date by Delegates.notNull()
        fun created(created: Date) = apply { this.created = created }

        private var updated: Date by Delegates.notNull()
        fun updated(updated: Date) = apply { this.updated = updated }

        private var deleted: Boolean by Delegates.notNull()
        fun deleted(deleted: Boolean) = apply { this.deleted = deleted }

        private var properties: Any? = null
        fun properties(properties: Any?) = apply { this.properties = properties }

        private fun required(): List<Any?> = listOf(id, type, name, target)

        fun build(): Template {
            if (required().any { it == null })
                throw IllegalArgumentException("One or more required values were null.")
            return when (this.type) {
                DirectoryTemplate.TYPE -> {
                    DirectoryTemplate(
                        id = id,
                        name = name,
                        description = description ?: "",
                        file = File(this.target),
                        config = DirectoryTemplateConfig()
                    )
                }
                else -> TODO("not supported type: ${this.type}")
            }
        }

    }


}