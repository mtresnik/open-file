package org.open.file.snapshot.store

import org.open.file.snapshot.store.domain.SnapshotNode

interface NodeDao {
    fun insert(node: SnapshotNode)
    fun insertAll(nodes: List<SnapshotNode>)
    fun getTree(snapshotId: String): SnapshotNode?
    fun getChildren(parentId: String): List<SnapshotNode>
    fun deleteBySnapshotId(snapshotId: String)
    fun readAll(): List<SnapshotNode>
}