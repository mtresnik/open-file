package org.open.file.snapshot

import org.open.file.snapshot.store.NodeDaoProvider
import org.open.file.snapshot.store.domain.SnapshotNode

class NodeService {

    private val dao = NodeDaoProvider.getDao()

    operator fun get(snapshotId: String): SnapshotNode? {
        return dao.getTree(snapshotId)
    }

    fun getAll(): List<SnapshotNode> {
        return dao.readAll()
    }

    operator fun set(snapshotId: String, node: SnapshotNode) {
        return dao.insert(node.apply { this.id = snapshotId })
    }

    fun create(node: SnapshotNode) {
        return dao.insert(node)
    }

}