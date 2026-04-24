package snapshot

import openfile.runCli
import org.open.file.snapshot.NodeService
import org.open.file.snapshot.SnapshotService
import org.open.file.snapshot.gen.TreeBuilder
import org.open.file.snapshot.models.SavedSnapshot
import org.open.file.snapshot.models.UnsavedSnapshot
import org.open.file.snapshot.store.domain.toDomain
import org.slf4j.LoggerFactory
import snapshot.cli.RootCommandLineHandler
import java.io.File
import kotlin.time.Clock

/** Service-layer orchestrator for `openfile snapshot <verb>`. */
class App {
    private val snapshots = SnapshotService()
    private val nodes = NodeService()
    private val log = LoggerFactory.getLogger(javaClass)

    fun list(): List<SavedSnapshot> {
        val all = snapshots.getAll()
            .filterIsInstance<SavedSnapshot>()
            .sortedByDescending { it.createdAt }
        log.info("Snapshot count: ${all.size}")
        all.forEach { println("${it.id}\t${it.createdAt}\t${it.rootPath}") }
        return all
    }

    fun create(rootPath: String): SavedSnapshot? {
        val dir = File(rootPath)
        if (!dir.exists() || !dir.isDirectory) {
            log.error("Path does not exist or is not a directory: $rootPath")
            return null
        }
        val rawTree = TreeBuilder.buildTree(dir)
        val unsaved = UnsavedSnapshot(rootPath = dir.absolutePath, createdAt = Clock.System.now())
        val saved = snapshots.create(unsaved) as? SavedSnapshot ?: run {
            log.error("Failed to persist snapshot header for $rootPath")
            return null
        }
        nodes.create(rawTree.toDomain(saved.id))
        log.info("Created snapshot ${saved.id} for $rootPath")
        println(saved.id)
        return saved
    }

    fun delete(id: String): Boolean {
        val existing = snapshots.getById(id) as? SavedSnapshot ?: run {
            log.error("No snapshot found with id: $id")
            return false
        }
        nodes.deleteBySnapshotId(id)
        val ok = snapshots.delete(existing)
        if (ok) log.info("Deleted snapshot $id") else log.error("Delete failed for $id")
        return ok
    }
}

fun runSnapshotCli(args: Array<String>) = runCli(RootCommandLineHandler(), args)
