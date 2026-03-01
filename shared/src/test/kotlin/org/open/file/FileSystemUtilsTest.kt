package org.open.file

import org.junit.jupiter.api.extension.ExtendWith
import org.open.file.template.utils.FileSystemUtils
import kotlin.test.Test
import kotlin.test.assertNotNull

class FileSystemUtilsTest {

    @Test
    @ExtendWith(SkipIfGitHub::class)
    fun `user home replacement documents`() {
        val path = "~/Documents"
        val file = FileSystemUtils.inferPath(path)
        assertNotNull(file, "File was null!")
    }

    @Test
    @ExtendWith(SkipIfGitHub::class)
    fun `user home replacement documents projects`() {
        // replace with a common directory on your machine
        val path = "~/Documents/Projects"
        val file = FileSystemUtils.inferPath(path)
    assertNotNull(file, "File was null!")
    }

}