package org.open.template

import org.open.template.models.directory.DirectoryTemplate
import org.open.template.models.directory.DirectoryTemplateConfig
import org.open.template.store.TemplateDao
import java.io.File
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

class TemplateDaoTest {

    private lateinit var dao: TemplateDao

    @BeforeTest
    fun `load database`() {
        dao = TemplateDao()
    }

    @Test
    fun `create template`() {
        val template = DirectoryTemplate(
            file = File("/home/mike/Documents/Projects/Templates/kotlin"),
            id = UUID.randomUUID(),
            name = "namo",
            config = DirectoryTemplateConfig()
        )
        dao.create(template)
    }

}