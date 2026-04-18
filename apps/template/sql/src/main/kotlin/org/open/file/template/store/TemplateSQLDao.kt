package org.open.file.template.org.open.file.template.store

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.open.file.template.models.Template
import org.open.file.shared.sql.adapters.DateAdapter
import org.open.file.shared.sql.adapters.UUIDAdapter
import org.open.file.store.models.StoreType
import org.open.file.template.Database
import org.open.file.utils.FileSystemUtils.home
import org.open.file.template.data.Templates
import org.open.file.template.org.open.file.template.store.adapters.TemplateTargetAdapter
import org.open.file.template.store.TemplateDao
import java.io.File
import java.util.*

class TemplateSQLDao : TemplateDao {

    private val templatesDatabase: File by lazy { home(TEMPLATES_DB_FILE_NAME).apply { parentFile.mkdirs() } }
    private val databaseUrl = "jdbc:sqlite:file:///${templatesDatabase.absolutePath}"
    val driver: SqlDriver = JdbcSqliteDriver(databaseUrl, Properties(), Database.Schema)

    private fun getDatabase(): Database {
        return Database(driver, Templates.Adapter(
            idAdapter       = UUIDAdapter,
            targetAdapter   = TemplateTargetAdapter,
            createdAdapter  = DateAdapter,
            updatedAdapter  = DateAdapter
        )
        )
    }

    fun listTypes(): List<String> {
        val database = getDatabase()
        val types = database.templateQueries.aggTypes().executeAsList()
        return types.flatMap { it.uppercase().split(",") }.distinct()
    }

    override fun readAll(): List<Template> {
        val database = getDatabase()
        val templates : List<Templates> = database.templateQueries.selectAll().executeAsList()
        return templates.toModel()
    }

    override val type: StoreType
        get() = StoreType.SQL

    override fun create(template: Template): Template {
        val database = getDatabase()
        template.created = Date()
        template.updated = Date()
        template.deleted = false
        database.templateQueries.insertFullTemplateObject(template.fromModel())
        return template
    }

    override fun read(id: String): Template? {
        val database = getDatabase()
        val databaseObject = database.templateQueries.findById(UUID.fromString(id)).executeAsOneOrNull()
        return databaseObject?.toModel()
    }

    override fun update(template: Template, upsert: Boolean) {
        val database = getDatabase()
        database.templateQueries.insertFullTemplateObject(template.fromModel())
    }

    override fun update(
        templateList: List<Template>,
        upsert: Boolean
    ) {
        val database = getDatabase()
        templateList.forEach { template ->
            database.templateQueries.insertFullTemplateObject(template.fromModel())
        }
    }

    override fun delete(template: Template): Boolean {
        val database = getDatabase()
        database.templateQueries.deleteById(template.id)
        return true
    }

    override fun deleteById(id: String): Boolean {
        val database = getDatabase()
        database.templateQueries.deleteById(UUID.fromString(id))
        return true
    }

    fun findById(id: UUID): Template? {
        return read(id.toString())
    }

    fun findByType(type: String): List<Template> {
        val database = getDatabase()
        val templates = database.templateQueries.selectType(type).executeAsList()
        return templates.toModel()
    }

    fun findByTarget(target: String): List<Template> {
        val database = getDatabase()
        val templates = database.templateQueries.selectTarget(target).executeAsList()
        return templates.toModel()
    }

    fun findByTypeAndTarget(type: String, target: String): List<Template> {
        val database = getDatabase()
        val templates = database.templateQueries.selectTypeAndTarget(type, target).executeAsList()
        return templates.toModel()
    }

}