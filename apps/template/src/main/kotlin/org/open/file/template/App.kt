package org.open.file.template

import org.open.file.template.cli.RootCommandLineHandler
import org.open.file.template.models.Template
import org.open.file.template.models.TemplateData
import org.open.file.template.models.TemplateFactory
import org.open.file.template.store.TemplateDao
import org.open.file.template.store.TemplateDaoProvider
import org.slf4j.LoggerFactory

class App {

    private val templateDao: TemplateDao = TemplateDaoProvider.getDao()

    fun list(): List<Template> {
        val logger = LoggerFactory.getLogger(javaClass)
        val templates = templateDao.list()
        logger.info("Template count: ${templates.size}")
        templates.forEach { template ->
            println(template.toString())
        }
        return templates
    }

    fun listTypes(): List<String> {
        val logger = LoggerFactory.getLogger(javaClass)
        // not sure why sqlite returns uppercase
        val types = templateDao.list().map { it.type }
        val typeUsage = mutableMapOf<String, Int>()
        TemplateFactory.availableTypes.forEach { type ->
            typeUsage[type] = 0
        }
        types.forEach { casedType ->
            val type = casedType.lowercase()
            if (type !in typeUsage) {
                logger.error("Invalid type detected: $type \t Was there a database migration?")
            } else {
                typeUsage[type] = typeUsage[type]!! + 1
            }
        }
        logger.info("Type count: ${typeUsage.keys.size}")
        typeUsage.forEach { (type, count) ->
            println("$type \t : \t $count")
        }
        return types
    }

    fun create(data: TemplateData): Template? {
        val logger = LoggerFactory.getLogger(javaClass)
        logger.info("Creating template...")
        val template = TemplateFactory[data]
        return templateDao.create(template)
    }

}

fun main(args: Array<String>) {
    val commandLineHandler = RootCommandLineHandler()
    val cmd = commandLineHandler.parse(args)
    if (args.isEmpty() || cmd == null) {
        while (true) {
            commandLineHandler.interactive()
        }
    }
    commandLineHandler.handle(cmd)
}