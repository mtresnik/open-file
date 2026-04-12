package org.open.file.template.org.open.file.template.store

import org.open.file.template.models.Template
import org.open.file.template.data.Templates
import org.open.file.template.org.open.file.template.store.adapters.TemplateTargetAdapter

fun Templates.toModel(): Template {
    return Template.Builder()
        .id(requireNotNull(this.id) { "Templates.id was null" })
        .target(TemplateTargetAdapter.getAdapterByType<Any>(this.type).encode(this.target))
        .name(this.name)
        .description(this.description)
        .type(this.type)
        .created(requireNotNull(this.created) { "Templates.created was null" } )
        .updated(requireNotNull(this.updated) { "Templates.updated was null" } )
        .deleted(requireNotNull(this.deleted) { "Templates.deleted was null" } )
        .build()
}

fun List<Templates>.toModel(): List<Template> = this.map(Templates::toModel)

fun Template.fromModel(): Templates {
    // TODO: add properties
    return Templates(
        id = this.id,
        this.type,
        this.name,
        this.description,
        this.target.toString(),
        this.created,
        this.updated,
        this.deleted,
        properties = byteArrayOf())
}