package org.open.file.template.store

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.open.file.Database
import org.open.file.template.models.Template
import org.open.file.template.store.sqldelight.adapters.DateAdapter
import org.open.file.template.store.sqldelight.adapters.UUIDAdapter
import org.open.file.template.utils.FileSystemUtils
import org.open.file.template.store.adapters.TemplateTargetAdapter
import org.open.template.template.data.Templates
import java.util.Date
import java.util.Properties
import java.util.UUID

class TemplateDao {

    private val databaseUrl = "jdbc:sqlite:file://${FileSystemUtils.templatesDatabase.absolutePath}"
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

    fun list(): List<Template> {
        val database = getDatabase()
        val templates : List<Templates> = database.templateQueries.selectAll().executeAsList()
        return templates.toModel()
    }

    fun create(entity: Template): Template {
        val database = getDatabase()
        entity.created = Date()
        entity.updated = Date()
        entity.deleted = false
        database.templateQueries.insertFullTemplateObject(entity.fromModel())
        return entity
    }

    fun save(list: List<Template>) {
        val database = getDatabase()
        list.forEach { template ->
            database.templateQueries.insertFullTemplateObject(template.fromModel())
        }
    }

    fun findById(id: UUID): Template? {
        val database = getDatabase()
        val databaseObject = database.templateQueries.findById(id).executeAsOneOrNull()
        return databaseObject?.toModel()
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