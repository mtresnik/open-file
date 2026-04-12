package org.open.file.template.models

import org.open.file.template.models.directory.DirectoryTemplate
import org.open.file.template.models.directory.DirectoryTemplateData

object TemplateFactory {

    private val typesToAliases = mutableMapOf<String, Collection<String>>().apply {
        put(DirectoryTemplate.TYPE, DirectoryTemplate.ALIASES)
    }
    val availableTypes = typesToAliases.keys

    fun getTypeForAlias(alias: String): String? {
        val match = typesToAliases.entries.firstOrNull { (_, aliases) ->
            alias in aliases
        }
        return match?.let { (type, _) -> type }
    }

    operator fun get(data: TemplateData): Template {
        val template = when (data) {
            is DirectoryTemplateData -> DirectoryTemplate.fromData(data)
            else -> TODO("Missing implementation")
        }
        return template
    }


}