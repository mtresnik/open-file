package org.open.file.snapshot.gen

import org.open.file.snapshot.models.RawDirectoryNode as DirectoryNode
import org.open.file.snapshot.models.RawFileNode as FileNode
import org.open.file.snapshot.models.RawNode as SnapshotNode
import org.open.file.utils.HashUtils.sha256
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

object TreeBuilder {

    /**
     * Progress tick emitted after each file is hashed. `filesScanned` is the
     * running count — we don't know the total ahead of time (that's what
     * this walk is computing), so UI callers render a scan phase as an
     * indeterminate progress indicator and just surface the current path.
     */
    data class ScanProgress(val filesScanned: Int, val currentPath: String)

    /**
     * Walk [root] and compute the hashed tree.
     *
     * [onProgress] fires per file after its hash completes, not during the
     * hash itself — so a single very-large file still blocks one tick. Good
     * enough for a UI spinner; don't rely on it for precision.
     *
     * [isCancelled] is polled before each new file is hashed. Returning
     * true throws [CancellationException], which a caller inside
     * `withContext` will receive as cooperative cancellation.
     */
    fun buildTree(
        root: File,
        onProgress: (ScanProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
        /**
         * Filter applied to every directory entry discovered during
         * the walk. Return `false` to skip the entry — and, for
         * directories, prune the entire subtree. Defaults to
         * "include everything", so existing callers keep their
         * previous behaviour.
         *
         * Applied per-entry (not to the root itself), so a caller
         * can scan `~/Documents/foo` with a filter that excludes
         * dotfiles without rejecting the root directory because
         * it happens to live under a hidden parent.
         */
        includeEntry: (File) -> Boolean = { true },
    ): SnapshotNode {
        if (root.isFile) {
            return FileNode(
                name = root.name,
                hash = sha256(root),
                path = root.absolutePath,
                size = root.length(),
                lastModified = root.lastModified()
            )
        }

        // Each frame captures its directory, a queue of entries still to visit
        // (sorted for deterministic ordering), and the child nodes we've
        // produced so far. Snapshotting listFiles() up-front avoids the
        // "which children have I already processed?" problem the previous
        // implementation had — there, the filter compared absolute paths from
        // listFiles to relative paths stored on children and never matched,
        // which infinite-looped on any subdirectory.
        data class Frame(
            val dir: File,
            val remaining: ArrayDeque<File>,
            val children: MutableList<SnapshotNode> = mutableListOf(),
        )

        fun newFrame(dir: File): Frame {
            // Apply `includeEntry` up-front so the queue is pre-
            // pruned. Skipped entries never get pushed, so neither
            // the file walk nor the directory recursion re-visits
            // them — important for perf on trees like ~/projects
            // where `.git/` can be the majority of the byte count.
            val entries = (dir.listFiles() ?: emptyArray())
                .filter { includeEntry(it) }
                .sortedBy { it.name }
            return Frame(dir = dir, remaining = ArrayDeque(entries))
        }

        val stack = ArrayDeque<Frame>()
        stack.addLast(newFrame(root))

        var result: DirectoryNode? = null
        var filesScanned = 0

        // Drive the walk until the root frame itself is finalized. The
        // previous loop used `while (stack.size > 1)` which never ran when
        // the root was the only frame — if the chosen directory had no
        // subdirectories, `buildTree` returned a DirectoryNode with an
        // empty `children` list regardless of what was actually on disk.
        while (stack.isNotEmpty()) {
            // Poll cancellation at the top of each iteration so a user
            // cancel lands quickly even inside deep directories.
            if (isCancelled()) throw CancellationException("TreeBuilder cancelled")

            val frame = stack.last()

            if (frame.remaining.isEmpty()) {
                // All entries visited — finalize this directory and either
                // attach it to its parent or, if it is the root, stash it
                // as the result.
                stack.removeLast()
                val hash = sha256(frame.children.joinToString { it.hash })
                val isRoot = stack.isEmpty()
                val node = DirectoryNode(
                    name = frame.dir.name,
                    // Root keeps its absolute path (matches the prior contract
                    // and what SnapshotRepository persists as rootPath); every
                    // descendant is stored relative to root for portability.
                    path = if (isRoot) frame.dir.absolutePath else frame.dir.relativeTo(root).path,
                    hash = hash,
                    children = frame.children
                )
                if (isRoot) result = node
                else stack.last().children.add(node)
                continue
            }

            val next = frame.remaining.removeFirst()
            when {
                next.isDirectory -> stack.addLast(newFrame(next))
                next.isFile -> {
                    frame.children.add(
                        FileNode(
                            name = next.name,
                            path = next.relativeTo(root).path,
                            hash = sha256(next),
                            size = next.length(),
                            lastModified = next.lastModified()
                        )
                    )
                    filesScanned++
                    onProgress(ScanProgress(filesScanned, next.absolutePath))
                }
                // Broken symlinks / special files (sockets, devices) fall through
                // and are skipped rather than crashing the walk.
            }
        }

        return result ?: DirectoryNode(
            name = root.name,
            path = root.absolutePath,
            hash = sha256(""),
            children = emptyList()
        )
    }

}