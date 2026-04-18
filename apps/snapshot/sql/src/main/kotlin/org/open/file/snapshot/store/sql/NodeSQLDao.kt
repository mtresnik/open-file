package org.open.file.snapshot.store.sql

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.open.file.snapshot.Database
import org.open.file.snapshot.Nodes
import org.open.file.snapshot.Snapshots
import org.open.file.snapshot.store.NodeDao
import org.open.file.snapshot.store.domain.DirectoryNode
import org.open.file.snapshot.store.domain.FileNode
import org.open.file.snapshot.store.domain.SnapshotNode
import org.open.file.snapshot.store.sql.toModelList
import org.open.file.utils.FileSystemUtils.home
import java.io.File
import java.util.Properties
import java.util.Stack

class NodeSQLDao : NodeDao {

    private val snapshotsDatabase: File by lazy { home(NODES_DB_FILE_NAME).apply { parentFile.mkdirs() } }
    private val databaseUrl = "jdbc:sqlite:file:///${snapshotsDatabase.absolutePath}"
    val driver: SqlDriver = JdbcSqliteDriver(databaseUrl, Properties(), Database.Schema)
    private val db = Database(driver)

    override fun insert(node: SnapshotNode) {
        db.transaction {
            insertNode(node)
        }
    }

    override fun insertAll(nodes: List<SnapshotNode>) {
        db.transaction {
            nodes.forEach { insertNode(it) }
        }
    }

    // Iteratively flatten and insert the tree
    private fun insertNode(root: SnapshotNode) {
        val stack = Stack<SnapshotNode>()
        stack.push(root)
        while (stack.isNotEmpty()) {
            when (val node = stack.pop()) {
                is FileNode -> {
                    db.nodesQueries.insert(
                        id = node.id,
                        snapshotId = node.snapshotId,
                        parentId = node.parentId,
                        type = NODE_TYPE_FILE,
                        name = node.name,
                        path = node.path,
                        hash = node.hash
                    )
                    db.file_metadataQueries.insert(
                        nodeId = node.id,
                        size = node.size,
                        lastModified = node.lastModified
                    )
                }
                is DirectoryNode -> {
                    db.nodesQueries.insert(
                        id = node.id,
                        snapshotId = node.snapshotId,
                        parentId = node.parentId,
                        type = NODE_TYPE_DIRECTORY,
                        name = node.name,
                        path = node.path,
                        hash = node.hash
                    )
                    node.children.forEach { stack.push(it) }
                }
            }
        }
    }

    override fun getTree(snapshotId: String): SnapshotNode {
        val allNodes = db.nodesQueries.getBySnapshotId(snapshotId).executeAsList()
        val allMetadata = db.file_metadataQueries.getBySnapshotId(snapshotId).executeAsList()
            .associateBy { it.nodeId }

        // Build a map of parentId -> children rows
        val childrenMap = allNodes.groupBy { it.parentId }

        // Find root — parentId is null
        val rootRow = allNodes.first { it.parentId == null }

        // Iteratively reconstruct the tree
        data class Frame(
            val nodeId: String,
            val children: MutableList<SnapshotNode> = mutableListOf()
        )

        val nodeMap = allNodes.associateBy { it.id }
        val stack = Stack<Frame>()
        val resultMap = mutableMapOf<String, SnapshotNode>()

        // Push in reverse BFS order so we build leaves first
        val ordered = mutableListOf<String>()
        val bfsQueue = ArrayDeque<String>()
        bfsQueue.add(rootRow.id)
        while (bfsQueue.isNotEmpty()) {
            val id = bfsQueue.removeFirst()
            ordered.add(id)
            childrenMap[id]?.forEach { bfsQueue.add(it.id) }
        }

        // Process leaves → root
        for (id in ordered.reversed()) {
            val row = nodeMap[id]!!
            when (row.type) {
                NODE_TYPE_FILE -> {
                    val meta = allMetadata[id]!!
                    resultMap[id] = FileNode(
                        id = row.id,
                        snapshotId = row.snapshotId,
                        parentId = row.parentId,
                        name = row.name,
                        path = row.path,
                        hash = row.hash,
                        size = meta.size,
                        lastModified = meta.lastModified
                    )
                }
                NODE_TYPE_DIRECTORY -> {
                    val children = childrenMap[id]
                        ?.mapNotNull { resultMap[it.id] }
                        ?: emptyList()
                    resultMap[id] = DirectoryNode(
                        id = row.id,
                        snapshotId = row.snapshotId,
                        parentId = row.parentId,
                        name = row.name,
                        path = row.path,
                        hash = row.hash,
                        children = children
                    )
                }
            }
        }

        return resultMap[rootRow.id]!!
    }

    override fun getChildren(parentId: String): List<SnapshotNode> {
        val rows = db.nodesQueries.getChildren(parentId).executeAsList()
        return rows.toModelList()
    }

    private fun List<Nodes>.toModelList(): List<SnapshotNode> {
        val metadata = this
            .filter { it.type == NODE_TYPE_FILE }
            .mapNotNull { db.file_metadataQueries.getByNodeId(it.id).executeAsOneOrNull() }
            .associateBy { it.nodeId }
        return this.map { row ->
            when (row.type) {
                NODE_TYPE_FILE -> {
                    val meta = metadata[row.id]!!
                    FileNode(
                        id = row.id,
                        snapshotId = row.snapshotId,
                        parentId = row.parentId,
                        name = row.name,
                        path = row.path,
                        hash = row.hash,
                        size = meta.size,
                        lastModified = meta.lastModified
                    )
                }
                else -> DirectoryNode(
                    id = row.id,
                    snapshotId = row.snapshotId,
                    parentId = row.parentId,
                    name = row.name,
                    path = row.path,
                    hash = row.hash,
                    children = emptyList() // shallow fetch
                )
            }
        }
    }

    override fun readAll(): List<SnapshotNode> {
        val database = db
        val snapshots : List<Nodes> = database.nodesQueries.selectAll().executeAsList()
        return snapshots.toModelList()
    }

    override fun deleteBySnapshotId(snapshotId: String) {
        db.nodesQueries.deleteBySnapshotId(snapshotId)
    }

    companion object {
        const val NODE_TYPE_FILE = "file"
        const val NODE_TYPE_DIRECTORY = "directory"
    }
}