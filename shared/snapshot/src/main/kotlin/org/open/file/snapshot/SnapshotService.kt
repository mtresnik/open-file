package org.open.file.snapshot

import org.open.file.snapshot.models.Snapshot
import org.open.file.snapshot.store.SnapshotDaoProvider

class SnapshotService {

    private val dao = SnapshotDaoProvider.getDao()

    fun getAll(): List<Snapshot> {
        return dao.readAll()
    }

    fun getById(id: String): Snapshot? {
        return dao.read(id)
    }

    fun create(snapshot: Snapshot): Snapshot? {
        return dao.create(snapshot)
    }

    /**
     * Delete the snapshot header. Does NOT cascade into the node tree —
     * callers that want the full graph gone should also call
     * [NodeService.deleteBySnapshotId].
     */
    fun delete(snapshot: Snapshot): Boolean {
        return dao.delete(snapshot)
    }

}