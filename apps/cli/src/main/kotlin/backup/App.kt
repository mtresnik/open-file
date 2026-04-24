package backup

import backup.cli.RootCommandLineHandler
import openfile.runCli
import org.open.file.archive.ArchiveExtractor
import org.open.file.archive.ArchiveFormat
import org.open.file.backup.BackupService
import org.open.file.backup.gen.BackupArchiver
import org.open.file.backup.models.CompressionType
import org.open.file.backup.models.SavedBackup
import org.slf4j.LoggerFactory
import java.io.File

/** Service-layer orchestrator for `openfile backup <verb>`. */
class App {
    private val service = BackupService()
    private val log = LoggerFactory.getLogger(javaClass)

    fun list(): List<SavedBackup> {
        val all = service.getAll()
            .filterIsInstance<SavedBackup>()
            .sortedByDescending { it.createdAt }
        log.info("Backup count: ${all.size}")
        all.forEach { println("${it.id}\t${it.createdAt}\t${it.rootPath}\t→ ${it.destinationPath}") }
        return all
    }

    fun create(sourcePath: String, targetDir: String?, includeHidden: Boolean): SavedBackup? {
        val source = File(sourcePath)
        if (!source.exists() || !source.isDirectory) {
            log.error("Source does not exist or is not a directory: $sourcePath")
            return null
        }
        val dir = File(targetDir ?: defaultTargetDir()).apply { mkdirs() }
        val archive = File(dir, defaultArchiveName(source))

        log.info("Archiving $sourcePath → ${archive.absolutePath}")
        val result = BackupArchiver.archive(
            source = source,
            destination = archive,
            compression = CompressionType.ZIP,
            includeHidden = includeHidden,
        )
        val saved = service.create(result.backup) as? SavedBackup ?: run {
            log.error("Failed to persist backup row for ${archive.absolutePath}")
            return null
        }
        log.info("Created backup ${saved.id}")
        println(saved.id)
        return saved
    }

    fun restore(id: String, destinationPath: String): Boolean {
        val existing = service.getById(id) as? SavedBackup ?: run {
            log.error("No backup found with id: $id")
            return false
        }
        val archive = File(existing.destinationPath)
        if (!archive.exists()) {
            log.error("Archive file missing: ${existing.destinationPath}")
            return false
        }
        val destination = File(destinationPath).apply { mkdirs() }
        log.info("Restoring ${existing.id} into ${destination.absolutePath}")
        val result = ArchiveExtractor.extract(archive, destination, ArchiveFormat.ZIP)
        log.info("Restore complete: ${result.fileCount} files, ${result.directoryCount} dirs, ${result.totalBytes} bytes")
        if (result.skipped.isNotEmpty()) {
            log.warn("Skipped ${result.skipped.size} entries — see log for per-entry reasons")
        }
        return true
    }

    fun delete(id: String): Boolean {
        val existing = service.getById(id) as? SavedBackup ?: run {
            log.error("No backup found with id: $id")
            return false
        }
        val archive = File(existing.destinationPath)
        val ok = service.delete(existing)
        if (ok) {
            // Archive removal is best-effort — the DB row is gone
            // either way, so a stranded file is an annoyance not a
            // correctness bug.
            runCatching { if (archive.exists()) archive.delete() }
            log.info("Deleted backup $id (archive: ${archive.absolutePath})")
        } else {
            log.error("Delete failed for $id")
        }
        return ok
    }

    private fun defaultTargetDir(): String =
        System.getProperty("user.home") + File.separator + ".open-file" + File.separator + "backups"

    private fun defaultArchiveName(source: File): String {
        val base = source.name.ifBlank { "backup" }
        return "$base-${System.currentTimeMillis()}.zip"
    }
}

fun runBackupCli(args: Array<String>) = runCli(RootCommandLineHandler(), args)
