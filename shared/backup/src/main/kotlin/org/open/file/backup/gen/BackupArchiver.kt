package org.open.file.backup.gen

import org.open.file.backup.models.CompressionType
import org.open.file.backup.models.UnsavedBackup
import org.open.file.snapshot.gen.TreeBuilder
import org.open.file.snapshot.models.RawDirectoryNode
import org.open.file.snapshot.models.RawFileNode
import org.open.file.snapshot.models.RawNode
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/**
 * Produces a compressed archive of a source directory.
 *
 * The archiver leans on [TreeBuilder] — the same walk the snapshot pipeline
 * uses — so every backup carries a fully-hashed tree of what was in the
 * directory at archive time (stats + future integrity checks come for free).
 * The tree is also what we iterate to feed the zip stream, which means the
 * archive entry order is deterministic across runs.
 */
object BackupArchiver {

    /**
     * Coarse pipeline phases the archiver reports through [onProgress].
     *
     * [SCANNING] covers the tree walk (file count is unknown until it
     * finishes, so UI renders an indeterminate indicator).
     * [COMPRESSING] covers the zip write (file count known; determinate
     * progress bar is appropriate).
     */
    enum class Phase { SCANNING, COMPRESSING }

    /**
     * A single progress tick. [totalFiles] is zero during SCANNING — use
     * `phase == COMPRESSING` as the signal to switch from indeterminate to
     * determinate UI. [currentFile] is the absolute path (scan) or the
     * archive-relative path (compress) of whatever's being processed right
     * now; handy as a status line in the dialog.
     */
    data class Progress(
        val phase: Phase,
        val filesProcessed: Int,
        val totalFiles: Int,
        val currentFile: String,
    )

    /**
     * Archive [source] into [destination]. [destination]'s parent directory
     * is created if it doesn't exist.
     *
     * [onProgress] fires per file during both phases — callers render a
     * progress dialog or silently ignore. [isCancelled] is polled between
     * files; returning true throws [CancellationException], which inside
     * a `withContext` block behaves as cooperative coroutine cancellation.
     * Partial archives created before cancellation are deleted on the way
     * out so we don't leave half-written zip files on disk.
     *
     * This call is blocking and can be slow for large trees — callers
     * should run it on an IO dispatcher.
     */
    fun archive(
        source: File,
        destination: File,
        compression: CompressionType = CompressionType.ZIP,
        onProgress: (Progress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
        /**
         * When false, entries matching the hidden-file convention
         * (Unix name starts with `.`, Windows has DOS hidden attr)
         * are excluded from both the tree walk and the zip output.
         * So `.git`, `.cache`, `.idea`, `.DS_Store` etc. never make
         * it into the archive. Default true preserves existing
         * behaviour for non-UI callers and tests.
         */
        includeHidden: Boolean = true,
    ): ArchiveResult {
        require(source.exists()) { "Source does not exist: ${source.absolutePath}" }
        require(source.isDirectory) { "Source is not a directory: ${source.absolutePath}" }
        require(source.canRead()) { "Source is not readable: ${source.absolutePath}" }

        destination.parentFile?.mkdirs()

        try {
            // Reuse the snapshot tree walk. Besides giving us hashes + counts
            // without re-scanning, iterating the tree (rather than walkTopDown)
            // means the zip entry order is stable regardless of filesystem
            // iteration order.
            //
            // The includeEntry filter prunes subtrees during the walk
            // (not after), so `.git/objects/pack/*` doesn't even get
            // hashed when hidden is excluded — matters on repos with
            // multi-gig pack files where hashing would dominate.
            val includeEntry: (File) -> Boolean = if (includeHidden) {
                { true }
            } else {
                // Covers both conventions: Unix name-starts-with-dot
                // and Windows DOS hidden attr. File.isHidden() uses
                // the right check per platform.
                { f -> !f.isHidden && !f.name.startsWith(".") }
            }
            val tree = TreeBuilder.buildTree(
                root = source,
                onProgress = { scan ->
                    onProgress(
                        Progress(
                            phase = Phase.SCANNING,
                            filesProcessed = scan.filesScanned,
                            totalFiles = 0,
                            currentFile = scan.currentPath,
                        )
                    )
                },
                isCancelled = isCancelled,
                includeEntry = includeEntry,
            ) as? RawDirectoryNode
                ?: error("Source ${source.absolutePath} didn't resolve to a directory tree")

            val originalSize = sumFileSizes(tree)
            val entryCount = countEntries(tree)
            val totalFiles = countFiles(tree)

            when (compression) {
                CompressionType.ZIP -> writeZip(
                    sourceRoot = source,
                    tree = tree,
                    destination = destination,
                    totalFiles = totalFiles,
                    onProgress = onProgress,
                    isCancelled = isCancelled,
                )
            }

            val backup = UnsavedBackup(
                rootPath = source.absolutePath,
                destinationPath = destination.absolutePath,
                // The parent dir the archive lives in — stashed on the
                // backup so "new backup from same source" / scheduled
                // reruns can place future archives alongside this one
                // without re-deriving it from the filename.
                targetDirectory = destination.parentFile?.absolutePath,
                createdAt = Clock.System.now(),
                compression = compression,
                originalSize = originalSize,
                archivedSize = destination.length(),
                entryCount = entryCount,
                snapshotId = null,
                // Stamped so "new backup from same source" in the UI
                // can replay with the exact toggle the user picked
                // originally — no more silent defaulting to `true`.
                includeHidden = includeHidden,
            )
            return ArchiveResult(backup = backup, tree = tree)
        } catch (t: Throwable) {
            // Partial archive left on disk after failure/cancel is worse
            // than no archive — delete and re-throw.
            runCatching { if (destination.exists()) destination.delete() }
            throw t
        }
    }

    /** Everything the caller needs to persist a backup and (optionally) an accompanying snapshot. */
    data class ArchiveResult(
        val backup: UnsavedBackup,
        /** The tree the archiver walked. Handy for callers that want to also write a snapshot. */
        val tree: RawNode,
    )

    // ──────────────────────────────────────────────
    // Zip writer
    // ──────────────────────────────────────────────

    private fun writeZip(
        sourceRoot: File,
        tree: RawDirectoryNode,
        destination: File,
        totalFiles: Int,
        onProgress: (Progress) -> Unit,
        isCancelled: () -> Boolean,
    ) {
        // Track files written across the whole recursive walk so progress
        // ticks read as "7 of 120" instead of per-directory counters.
        var filesWritten = 0
        ZipOutputStream(BufferedOutputStream(FileOutputStream(destination))).use { zip ->
            // Walk the tree recursively, writing each directory as an empty
            // entry (so empty dirs round-trip through the archive) and each
            // file as its contents. Zip entry names use forward slashes
            // regardless of host OS, per the zip spec.
            filesWritten = writeEntries(
                zip = zip,
                sourceRoot = sourceRoot,
                node = tree,
                entryPathPrefix = "",
                filesWritten = filesWritten,
                totalFiles = totalFiles,
                onProgress = onProgress,
                isCancelled = isCancelled,
            )
        }
    }

    private fun writeEntries(
        zip: ZipOutputStream,
        sourceRoot: File,
        node: RawDirectoryNode,
        entryPathPrefix: String,
        filesWritten: Int,
        totalFiles: Int,
        onProgress: (Progress) -> Unit,
        isCancelled: () -> Boolean,
    ): Int {
        var written = filesWritten

        // The zip spec wants directory entries to end in "/". We emit one for
        // every directory except the archive root so an extractor recreates
        // the full hierarchy even when a dir is empty.
        if (entryPathPrefix.isNotEmpty()) {
            zip.putNextEntry(ZipEntry("$entryPathPrefix/"))
            zip.closeEntry()
        }

        node.children.forEach { child ->
            if (isCancelled()) throw CancellationException("BackupArchiver cancelled")

            val childEntryPath = if (entryPathPrefix.isEmpty()) child.name
            else "$entryPathPrefix/${child.name}"

            when (child) {
                is RawFileNode -> {
                    val entry = ZipEntry(childEntryPath)
                    // Preserve mtime so extraction tools don't all stamp
                    // everything with today's date.
                    entry.time = child.lastModified
                    zip.putNextEntry(entry)
                    // Resolve the actual file on disk by its relative path
                    // from source root; the RawFileNode already holds that
                    // path (see TreeBuilder).
                    val onDisk = File(sourceRoot, child.path)
                    onDisk.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()

                    written++
                    onProgress(
                        Progress(
                            phase = Phase.COMPRESSING,
                            filesProcessed = written,
                            totalFiles = totalFiles,
                            currentFile = childEntryPath,
                        )
                    )
                }
                is RawDirectoryNode -> written = writeEntries(
                    zip = zip,
                    sourceRoot = sourceRoot,
                    node = child,
                    entryPathPrefix = childEntryPath,
                    filesWritten = written,
                    totalFiles = totalFiles,
                    onProgress = onProgress,
                    isCancelled = isCancelled,
                )
            }
        }
        return written
    }

    // ──────────────────────────────────────────────
    // Tree walking helpers
    // ──────────────────────────────────────────────

    private fun sumFileSizes(node: RawNode): Long = when (node) {
        is RawFileNode -> node.size
        is RawDirectoryNode -> node.children.sumOf { sumFileSizes(it) }
    }

    private fun countEntries(node: RawNode): Int = when (node) {
        is RawFileNode -> 1
        is RawDirectoryNode -> 1 + node.children.sumOf { countEntries(it) }
    }

    /** Total file entries (excluding directories), used as the denominator for COMPRESSING progress. */
    private fun countFiles(node: RawNode): Int = when (node) {
        is RawFileNode -> 1
        is RawDirectoryNode -> node.children.sumOf { countFiles(it) }
    }
}
