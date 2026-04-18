package org.open.file.snapshot.gen

import org.open.file.snapshot.models.Change
import org.open.file.snapshot.store.domain.DirectoryNode
import org.open.file.snapshot.store.domain.FileNode
import org.open.file.snapshot.store.domain.SnapshotNode
import java.util.Stack

object DiffBuilder {

    fun diff(old: DirectoryNode, new: DirectoryNode): List<Change> {
        val changes = mutableListOf<Change>()

        data class Frame(val old: DirectoryNode, val new: DirectoryNode)

        val stack = Stack<Frame>()
        stack.push(Frame(old, new))

        while (stack.isNotEmpty()) {
            val (oldDir, newDir) = stack.pop()

            if (oldDir.hash == newDir.hash) continue

            // Mark this directory as partially changed
            changes.add(Change.Partial(newDir))

            val oldChildren = oldDir.children.associateBy { it.name }
            val newChildren = newDir.children.associateBy { it.name }

            val allNames = oldChildren.keys + newChildren.keys

            for (name in allNames) {
                val oldChild = oldChildren[name]
                val newChild = newChildren[name]

                when {
                    // Added
                    oldChild == null && newChild is FileNode -> changes.add(Change.Added(newChild))
                    oldChild == null && newChild is DirectoryNode -> collectAdded(newChild, changes)

                    // Deleted
                    newChild == null && oldChild is FileNode -> changes.add(Change.Deleted(oldChild))
                    newChild == null && oldChild is DirectoryNode -> collectDeleted(oldChild, changes)

                    // Both exist
                    oldChild is FileNode && newChild is FileNode -> {
                        if (oldChild.hash != newChild.hash) changes.add(Change.Modified(oldChild, newChild))
                    }
                    oldChild is DirectoryNode && newChild is DirectoryNode -> {
                        stack.push(Frame(oldChild, newChild)) // recurse into dir
                    }
                    // File became a directory or vice versa
                    oldChild is FileNode && newChild is DirectoryNode -> {
                        changes.add(Change.Deleted(oldChild))
                        collectAdded(newChild, changes)
                    }
                    oldChild is DirectoryNode && newChild is FileNode -> {
                        collectDeleted(oldChild, changes)
                        changes.add(Change.Added(newChild))
                    }
                }
            }
        }

        return changes
    }

    // When a whole directory is new, collect all files inside as Added
    private fun collectAdded(dir: DirectoryNode, changes: MutableList<Change>) {
        val stack = Stack<SnapshotNode>().also {
            it.push(dir)
        }
        while (stack.isNotEmpty()) {
            when (val node = stack.pop()) {
                is FileNode -> changes.add(Change.Added(node))
                is DirectoryNode -> {
                    changes.add(Change.Added(node))
                    stack.addAll(node.children)
                }
            }
        }
    }

    // When a whole directory is gone, collect all files inside as Deleted
    private fun collectDeleted(dir: DirectoryNode, changes: MutableList<Change>) {
        val stack = Stack<SnapshotNode>()
        stack.push(dir)
        while (stack.isNotEmpty()) {
            when (val node = stack.pop()) {
                is FileNode -> changes.add(Change.Deleted(node))
                is DirectoryNode -> {
                    changes.add(Change.Deleted(node))
                    stack.addAll(node.children)
                }
            }
        }
    }

}