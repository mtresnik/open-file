package org.open.file.snapshot.gen

import org.open.file.snapshot.models.RawDirectoryNode as DirectoryNode
import org.open.file.snapshot.models.RawFileNode as FileNode
import org.open.file.snapshot.models.RawNode as SnapshotNode
import org.open.file.utils.HashUtils.sha256
import java.io.File
import java.util.*

object TreeBuilder {

    fun buildTree(root: File): SnapshotNode {
        if (root.isFile) {
            return FileNode(
                name = root.name,
                hash = sha256(root),
                path = root.absolutePath,
                size = root.length(),
                lastModified = root.lastModified()
            )
        }

        // Each stack frame holds a directory and its already-processed children
        data class Frame(val dir: File, val children: MutableList<SnapshotNode> = mutableListOf())

        val stack = Stack<Frame>()
        stack.push(Frame(root))

        while (stack.size > 1) {
            val frame = stack.peek()

            // Find the next unprocessed child
            val processedPaths = frame.children.map { it.path }.toSet()
            val next = frame.dir.listFiles()
                ?.filter { it.absolutePath !in processedPaths }
                ?.minByOrNull { it.name } // deterministic order

            when {
                next == null -> {
                    // All children processed — finalize this directory
                    stack.pop()
                    val hash = sha256(frame.children.joinToString { it.hash })
                    val node = DirectoryNode(
                        name = frame.dir.name,
                        path = frame.dir.relativeTo(root).path,
                        hash = hash,
                        children = frame.children
                    )
                    stack.peek().children.add(node)
                }
                next.isDirectory -> stack.push(Frame(next))
                next.isFile -> {
                    val hash = sha256(next)
                    frame.children.add(
                        FileNode(
                            name = next.name,
                            path = next.relativeTo(root).path,
                            hash = hash,
                            size = next.length(),
                            lastModified = next.lastModified()
                        )
                    )
                }
            }
        }

        // Finalize root
        val rootFrame = stack.pop()
        val rootHash = sha256(rootFrame.children.joinToString { it.hash })
        return DirectoryNode(
            name = root.name,
            path = root.absolutePath,
            hash = rootHash,
            children = rootFrame.children
        )
    }

}