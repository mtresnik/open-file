package org.open.file.snapshot.models

import java.util.UUID
import kotlin.time.Instant

interface Snapshot {
    val rootPath: String
    val createdAt: Instant


    fun toSaved(id: String = UUID.randomUUID().toString()): SavedSnapshot {
        return SavedSnapshot(id, rootPath, createdAt)
    }

}

data class UnsavedSnapshot(
    override val rootPath: String,
    override val createdAt: Instant,
) : Snapshot {


}

data class SavedSnapshot(
    val id: String,
    override val rootPath: String,
    override val createdAt: Instant,
) : Snapshot {

    override fun toSaved(id: String): SavedSnapshot {
        return this
    }
}