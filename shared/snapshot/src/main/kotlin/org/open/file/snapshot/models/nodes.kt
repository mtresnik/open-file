package org.open.file.snapshot.models

// internal to the snapshot module, not in domain
sealed class RawNode {
    abstract val name: String
    abstract val hash: String
    abstract val path: String
}

internal data class RawFileNode(
    override val name: String,
    override val hash: String,
    override val path: String,
    val size: Long,
    val lastModified: Long,
) : RawNode()

internal data class RawDirectoryNode(
    override val name: String,
    override val hash: String,
    override val path: String,
    val children: List<RawNode>,
) : RawNode()