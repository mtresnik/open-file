package org.open.file.snapshot.store

import org.open.file.snapshot.models.Snapshot
import org.open.file.store.models.StoreType

interface SnapshotDao {

    val type: StoreType

    fun create(snapshot: Snapshot): Snapshot?

    fun read(id: String): Snapshot?

    fun readAll(): List<Snapshot>

    fun update(snapshot: Snapshot, upsert: Boolean = true)

    fun update(snapshotList: List<Snapshot>, upsert: Boolean = true)

    fun delete(snapshot: Snapshot): Boolean

}