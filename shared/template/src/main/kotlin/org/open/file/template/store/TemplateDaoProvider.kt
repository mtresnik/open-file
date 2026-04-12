package org.open.file.template.store

import java.util.*

object TemplateDaoProvider {

    fun getDao(): TemplateDao {
        return requireNotNull(ServiceLoader.load(TemplateDao::class.java).firstOrNull())
    }

}