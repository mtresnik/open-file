package org.open.template

import org.open.file.template.utils.FileSystemUtils
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertNotNull

class FileSystemUtilsTest {

    @Test
    fun `user home replacement documents`() {
        val path = "~/Documents"
        val file = FileSystemUtils.inferPath(path)
        assertNotNull(file, "File was null!")
    }

//    @Test
    @Ignore
    fun `user home replacement documents projects`() {
        // replace with a common directory on your machine
        val path = "~/Documents/Projects"
        val file = FileSystemUtils.inferPath(path)
        assertNotNull(file, "File was null!")
    }

//    @Test
    @Ignore
    fun `user home replacement documents projects open template`() {
        // replace with a common directory on your machine
        val path = "~/Documents/Projects/open-file"
        val file = FileSystemUtils.inferPath(path)
        assertNotNull(file, "File was null!")
    }

}