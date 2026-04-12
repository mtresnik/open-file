package org.open.file.template.models.directory

import org.open.file.template.models.TemplateData
import org.open.file.template.models.directory.DirectoryTemplate.Companion.TYPE
import java.io.File
import java.io.IOException
import java.util.*

class DirectoryTemplateData(var file: File,
                            override val type: String = TYPE,
                            override val name: String,
                            override val description: String,
                            override var created: Date = Date(),
                            override var updated: Date = Date(),
                            override var deleted: Boolean = false)
    : TemplateData(
        type = type,
        name = name,
        description = description,
        created = created,
        updated = updated,
        deleted = deleted) {

    companion object {

        fun fromTemplateData(data: TemplateData, file: File) : Result<DirectoryTemplateData> {
            if (!file.exists()) { return Result.failure(IOException("Missing file")) }
            return Result.success(DirectoryTemplateData(
                file = file,
                name = data.name,
                description = data.description,
                created = data.created,
                updated = data.updated,
                deleted = data.deleted
            ))
        }

    }

}