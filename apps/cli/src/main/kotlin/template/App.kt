package template

import openfile.runCli
import org.open.file.template.models.Template
import org.open.file.template.models.TemplateData
import org.open.file.template.models.TemplateFactory
import org.open.file.template.store.TemplateDao
import org.open.file.template.store.TemplateDaoProvider
import org.slf4j.LoggerFactory
import template.cli.RootCommandLineHandler

/** Service-layer orchestrator for `openfile template <verb>`. */
class App {
    private val dao: TemplateDao = TemplateDaoProvider.getDao()
    private val log = LoggerFactory.getLogger(javaClass)

    fun list(): List<Template> {
        val templates = dao.readAll()
        log.info("Template count: ${templates.size}")
        templates.forEach { println(it.toString()) }
        return templates
    }

    fun listTypes(): List<String> {
        val types = dao.readAll().map { it.type }
        val usage = TemplateFactory.availableTypes.associateWith { 0 }.toMutableMap()
        types.forEach { cased ->
            val type = cased.lowercase()
            if (type !in usage) {
                // sqlite sometimes returns uppercased types — flag it so a
                // future migration can be traced back to this mismatch.
                log.error("Invalid type detected: $type \t Was there a database migration?")
            } else {
                usage[type] = usage[type]!! + 1
            }
        }
        log.info("Type count: ${usage.keys.size}")
        usage.forEach { (type, count) -> println("$type \t : \t $count") }
        return types
    }

    fun create(data: TemplateData): Template? {
        log.info("Creating template...")
        return dao.create(TemplateFactory[data])
    }
}

fun runTemplateCli(args: Array<String>) = runCli(RootCommandLineHandler(), args)
