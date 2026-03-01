package org.open.file.template.models.directory

import org.open.file.template.models.Template
import java.io.File
import java.util.Date
import java.util.UUID

data class DirectoryTemplate(val file: File,
                             override var id: UUID,
                             override val type: String = TYPE,
                             override val name: String,
                             override val description: String = "",
                             override val target: Any = file,
                             override val config: DirectoryTemplateConfig = DirectoryTemplateConfig(),
                             override var created: Date = Date(),
                             override var updated: Date = Date(),
                             override var deleted: Boolean = false,
    ): Template(
        id= id,
        type= TYPE,
        name= name,
        description = description,
        target= file,
        created= created,
        updated= updated,
        deleted= deleted,
        config = config) {

    val isValid: Boolean
        get() = run {
            this.file.exists() && this.file.isDirectory
        }

    companion object {
        const val TYPE = "directory"
        val ALIASES = listOf("dir", "directory")


        fun fromData(data: DirectoryTemplateData, config: DirectoryTemplateConfig = DirectoryTemplateConfig()): DirectoryTemplate {
            return DirectoryTemplate(
                file = data.file,
                id = UUID.randomUUID(),
                type = data.type,
                name = data.name,
                description = data.description,
                target = data.file,
                config = config,
                created = data.created,
                updated = data.updated,
                deleted = data.deleted
            )
        }

    }

}
