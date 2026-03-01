package org.open.template

import org.open.file.template.utils.FileSystemUtils
import kotlin.test.Test
import kotlin.test.assertNotNull

class FileSystemUtilsTest {

    @Test
    fun `user home replacement documents`() {
        val path = "~/Documents"
        val file = FileSystemUtils.inferPath(path)
        assertNotNull(file, "File was null!")
    }

    @Test
    fun `user home replacement documents projects`() {
        val path = "~/Documents/Projects"
        val file = FileSystemUtils.inferPath(path)
        assertNotNull(file, "File was null!")
    }

    @Test
    fun `user home replacement documents projects open template`() {
        val path = "~/Documents/Projects/open-file"
        val file = FileSystemUtils.inferPath(path)
        assertNotNull(file, "File was null!")
    }

}