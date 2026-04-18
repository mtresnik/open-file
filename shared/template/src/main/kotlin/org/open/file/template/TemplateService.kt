package org.open.file.template

import org.open.file.Service
import org.open.file.template.models.Template
import org.open.file.template.store.TemplateDaoProvider

class TemplateService : Service<Template> {

    private val dao = TemplateDaoProvider.getDao()

    operator fun get(id: String): Template? {
        return dao.read(id)
    }

    override fun getAll(): List<Template> {
        return dao.readAll()
    }

    override operator fun set(id: String, entity: Template): Template {
        dao.update(entity)
        return entity
    }

    override fun create(entity: Template): Template? {
        return dao.create(entity)
    }

    override fun delete(id: String): Boolean {
        return dao.deleteById(id)
    }

}