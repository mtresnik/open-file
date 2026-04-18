package org.open.file.snapshot.gen

import org.junit.jupiter.api.Test
import org.open.file.snapshot.models.Change
import org.open.file.snapshot.store.domain.DirectoryNode
import org.open.file.snapshot.store.domain.FileNode
import org.open.file.snapshot.store.domain.SnapshotNode
import org.open.file.utils.HashUtils
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DiffTest {

    private fun file(name: String, hash: String, path: String = name) = FileNode(
        id = UUID.randomUUID().toString(),
        snapshotId = TEST_SNAPSHOT_ID,
        parentId = null,
        name = name,
        hash = hash,
        path = path,
        size = 0L,
        lastModified = 0L
    )

    private fun dir(name: String, path: String = name, children: List<SnapshotNode>) = DirectoryNode(
        id = UUID.randomUUID().toString(),
        snapshotId = TEST_SNAPSHOT_ID,
        parentId = null,
        name = name,
        hash = HashUtils.sha256(children.sortedBy { it.name }.joinToString { it.hash }),
        path = path,
        children = children
    )

    @Test
    fun `unchanged tree produces no changes`() {
        val tree = dir("root", "", listOf(file("Main.kt", "abc")))
        assertEquals(emptyList(), DiffBuilder.diff(tree, tree))
    }

    @Test
    fun `added file is detected`() {
        val old = dir("root", "", listOf())
        val new = dir("root", "", listOf(file("Main.kt", "abc")))

        val changes = DiffBuilder.diff(old, new)

        assertEquals(1, changes.filterIsInstance<Change.Partial>().size)
        assertEquals(1, changes.filterIsInstance<Change.Added>().size)
        assertIs<FileNode>(changes.filterIsInstance<Change.Added>().first().node)
    }

    @Test
    fun `deleted file is detected`() {
        val old = dir("root", "", listOf(file("Main.kt", "abc")))
        val new = dir("root", "", listOf())

        val changes = DiffBuilder.diff(old, new)

        assertEquals(1, changes.filterIsInstance<Change.Partial>().size)
        assertEquals(1, changes.filterIsInstance<Change.Deleted>().size)
        assertIs<FileNode>(changes.filterIsInstance<Change.Deleted>().first().node)
    }

    @Test
    fun `modified file is detected`() {
        val old = dir("root", "", listOf(file("Main.kt", "abc")))
        val new = dir("root", "", listOf(file("Main.kt", "xyz")))

        val changes = DiffBuilder.diff(old, new)

        assertEquals(1, changes.filterIsInstance<Change.Partial>().size)
        val modified = changes.filterIsInstance<Change.Modified>().first()
        assertEquals("abc", modified.old.hash)
        assertEquals("xyz", modified.new.hash)
    }

    @Test
    fun `added directory marks all children as added`() {
        val addedDir = dir("utils", "utils", listOf(
            file("Foo.kt", "foo", "utils/Foo.kt"),
            file("Bar.kt", "bar", "utils/Bar.kt")
        ))
        val old = dir("root", "", listOf())
        val new = dir("root", "", listOf(addedDir))

        val changes = DiffBuilder.diff(old, new)

        val added = changes.filterIsInstance<Change.Added>()
        assertEquals(3, added.size) // dir + 2 files
    }

    @Test
    fun `deleted directory marks all children as deleted`() {
        val deletedDir = dir("utils", "utils", listOf(
            file("Foo.kt", "foo", "utils/Foo.kt"),
            file("Bar.kt", "bar", "utils/Bar.kt")
        ))
        val old = dir("root", "", listOf(deletedDir))
        val new = dir("root", "", listOf())

        val changes = DiffBuilder.diff(old, new)

        val deleted = changes.filterIsInstance<Change.Deleted>()
        assertEquals(3, deleted.size) // dir + 2 files
    }

    @Test
    fun `unchanged subtree is skipped`() {
        val unchanged = dir("unchanged", "unchanged", listOf(file("Foo.kt", "foo", "unchanged/Foo.kt")))
        val old = dir("root", "", listOf(unchanged, file("Main.kt", "abc")))
        val new = dir("root", "", listOf(unchanged, file("Main.kt", "xyz")))

        val changes = DiffBuilder.diff(old, new)

        // Only root partial + Main.kt modified — unchanged/ skipped entirely
        assertEquals(1, changes.filterIsInstance<Change.Partial>().size)
        assertEquals(1, changes.filterIsInstance<Change.Modified>().size)
        assertEquals(0, changes.filterIsInstance<Change.Added>().size)
        assertEquals(0, changes.filterIsInstance<Change.Deleted>().size)
    }

    @Test
    fun `file replaced by directory`() {
        val old = dir("root", "", listOf(file("utils", "abc")))
        val new = dir("root", "", listOf(dir("utils", "utils", listOf(file("Foo.kt", "foo", "utils/Foo.kt")))))

        val changes = DiffBuilder.diff(old, new)

        assertEquals(1, changes.filterIsInstance<Change.Deleted>().size) // old file
        assertEquals(2, changes.filterIsInstance<Change.Added>().size)   // new dir + new file
    }

    @Test
    fun `directory replaced by file`() {
        val old = dir("root", "", listOf(dir("utils", "utils", listOf(file("Foo.kt", "foo", "utils/Foo.kt")))))
        val new = dir("root", "", listOf(file("utils", "abc")))

        val changes = DiffBuilder.diff(old, new)

        assertEquals(2, changes.filterIsInstance<Change.Deleted>().size) // old dir + old file
        assertEquals(1, changes.filterIsInstance<Change.Added>().size)   // new file
    }


    companion object {
        const val TEST_SNAPSHOT_ID = "test-snapshot"
    }
}