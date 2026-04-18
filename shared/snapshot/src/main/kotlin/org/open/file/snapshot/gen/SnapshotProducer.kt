package org.open.file.snapshot.gen

import org.open.file.snapshot.models.Snapshot
import org.open.file.snapshot.models.UnsavedSnapshot
import java.io.File
import kotlin.time.Clock

class SnapshotProducer {

    fun getSnapshot(dir: File): Snapshot? {
        if (!dir.isDirectory || !dir.exists()) return null
        val node = TreeBuilder.buildTree(dir)
        return UnsavedSnapshot(
            rootPath = dir.absolutePath,
            createdAt = Clock.System.now()
        )
    }

}