// SnapshotNode.kt
package org.open.file.snapshot.store.domain

import kotlinx.serialization.Serializable
import org.open.file.snapshot.models.RawDirectoryNode
import org.open.file.snapshot.models.RawFileNode
import org.open.file.snapshot.models.RawNode
import java.util.UUID

@Serializable
sealed class SnapshotNode {
    abstract var id: String
    abstract val snapshotId: String
    abstract val parentId: String?
    abstract val name: String
    abstract val path: String
    abstract val hash: String
}

@Serializable
data class FileNode(
    override var id: String,
    override val snapshotId: String,
    override val parentId: String?,
    override val name: String,
    override val path: String,
    override val hash: String,
    val size: Long,
    val lastModified: Long,
) : SnapshotNode()

@Serializable
data class DirectoryNode(
    override var id: String,
    override val snapshotId: String,
    override val parentId: String?,
    override val name: String,
    override val path: String,
    override val hash: String,
    val children: List<SnapshotNode>,
) : SnapshotNode()

fun RawNode.toDomain(snapshotId: String, parentId: String? = null): SnapshotNode {
    val id = UUID.randomUUID().toString()
    return when (this) {
        is RawFileNode -> FileNode(
            id = id,
            snapshotId = snapshotId,
            parentId = parentId,
            name = name,
            path = path,
            hash = hash,
            size = size,
            lastModified = lastModified,
        )
        is RawDirectoryNode -> DirectoryNode(
            id = id,
            snapshotId = snapshotId,
            parentId = parentId,
            name = name,
            path = path,
            hash = hash,
            children = children.map { it.toDomain(snapshotId, id) }
        )
    }
}