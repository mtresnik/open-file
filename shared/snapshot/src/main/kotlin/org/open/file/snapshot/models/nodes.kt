package org.open.file.snapshot.models

/**
 * Pre-persistence tree built by [org.open.file.snapshot.gen.TreeBuilder].
 *
 * These used to be `internal` so no one outside :shared:snapshot could
 * accidentally fabricate a tree, but the backup module needs to walk the
 * tree to drive its zip writer and reads are harmless. Keep them public;
 * construction is still effectively gated behind TreeBuilder.
 */
sealed class RawNode {
    abstract val name: String
    abstract val hash: String
    abstract val path: String
}

data class RawFileNode(
    override val name: String,
    override val hash: String,
    override val path: String,
    val size: Long,
    val lastModified: Long,
) : RawNode()

data class RawDirectoryNode(
    override val name: String,
    override val hash: String,
    override val path: String,
    val children: List<RawNode>,
) : RawNode()