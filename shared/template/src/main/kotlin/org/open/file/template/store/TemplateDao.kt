package org.open.file.template.store

import org.open.file.template.models.Template
import org.open.file.store.models.StoreType

interface TemplateDao {

    val type: StoreType

    fun create(template: Template): Template?

    fun read(id: String): Template?

    fun update(template: Template, upsert: Boolean = true)

    fun update(templateList: List<Template>, upsert: Boolean = true)

    fun delete(template: Template): Boolean

    fun list(): List<Template>

}