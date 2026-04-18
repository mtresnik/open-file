package org.open.file.snapshot.models

import org.open.file.snapshot.store.domain.DirectoryNode
import org.open.file.snapshot.store.domain.FileNode
import org.open.file.snapshot.store.domain.SnapshotNode

sealed class Change {
    data class Added(val node: SnapshotNode) : Change()
    data class Deleted(val node: SnapshotNode) : Change()
    data class Modified(val old: FileNode, val new: FileNode) : Change()
    data class Partial(val node: DirectoryNode) : Change()
}