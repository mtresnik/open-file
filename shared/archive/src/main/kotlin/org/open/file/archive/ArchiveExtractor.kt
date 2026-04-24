package org.open.file.archive

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

/**
 * Expand an archive onto disk.
 *
 * Used by the Restore-Backup flow and by zip-bundled template
 * scaffolding — two surfaces sharing one zip pipeline. Before this
 * module existed, the same code lived inside `:shared:backup`'s
 * `BackupExtractor`; consumers in `:desktop-ui` had to reach through
 * the backup module to get at a purely-generic zip reader. Promoting
 * it here keeps the backup module focused on domain types and lets
 * other features (template scaffolding, future export flows) depend
 * on the extractor without dragging in `Backup` / `SavedBackup` /
 * `CompressionType`.
 *
 * The extractor is deliberately paranoid about entry paths: each
 * resolved target is canonicalised and checked against the
 * destination root, so a malicious archive containing
 * `../../etc/passwd` entries can't escape (the "zip slip" class of
 * CVEs).
 *
 * Call this from an IO dispatcher — it streams file bytes directly
 * to disk and can block for a while on large archives.
 */
object ArchiveExtractor {

    /**
     * Extract [archive] into [destination]. [destination] is created
     * if it doesn't exist. Existing files with overlapping paths are
     * silently overwritten — the UI is expected to surface that risk
     * to the user before calling (restore dialogs, etc.).
     *
     * Returns [ExtractResult] with the number of files / directories
     * emitted and the cumulative byte count, so the caller can show
     * a summary without re-scanning the destination.
     */
    fun extract(
        archive: File,
        destination: File,
        format: ArchiveFormat = ArchiveFormat.ZIP,
    ): ExtractResult {
        require(archive.exists()) { "Archive does not exist: ${archive.absolutePath}" }
        require(archive.isFile) { "Archive is not a regular file: ${archive.absolutePath}" }
        require(archive.canRead()) { "Archive is not readable: ${archive.absolutePath}" }

        destination.mkdirs()

        return when (format) {
            ArchiveFormat.ZIP -> extractZip(archive, destination)
        }
    }

    /** Summary of an extract pass. */
    data class ExtractResult(
        val fileCount: Int,
        val directoryCount: Int,
        val totalBytes: Long,
        /**
         * Entries we couldn't write — most commonly Windows files
         * inside `.git/objects/` marked read-only, or files a
         * concurrent process was holding open. Callers decide
         * whether to surface this in the UI (restore) or fail the
         * whole operation (strict modes). Empty when extraction
         * finished cleanly.
         */
        val skipped: List<SkippedEntry> = emptyList(),
    )

    /** One entry that couldn't be written, with the underlying reason. */
    data class SkippedEntry(val path: String, val reason: String)

    private fun extractZip(archive: File, destination: File): ExtractResult {
        val canonicalDest = destination.canonicalFile
        var fileCount = 0
        var dirCount = 0
        var total = 0L
        val skipped = mutableListOf<SkippedEntry>()

        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                val target = File(canonicalDest, entry.name)

                // Zip-slip defence: resolve the target canonically and
                // verify it still lives inside the destination root.
                // Without this a crafted entry name like
                // "../../etc/passwd" would land outside the user's
                // chosen directory.
                val canonicalTarget = target.canonicalFile
                if (!canonicalTarget.path.startsWith(canonicalDest.path + File.separator) &&
                    canonicalTarget.path != canonicalDest.path
                ) {
                    error("Refusing to extract entry outside destination: ${entry.name}")
                }

                if (entry.isDirectory) {
                    if (!canonicalTarget.exists()) canonicalTarget.mkdirs()
                    dirCount++
                } else {
                    // Parents may not have explicit entries (e.g.
                    // older zips written without directory records) —
                    // create the full ancestor chain.
                    canonicalTarget.parentFile?.mkdirs()

                    // Windows commonly marks files as read-only,
                    // especially anything inside a `.git/` tree. A
                    // plain FileOutputStream.write throws
                    // AccessDenied on those without the attribute
                    // first being cleared. Preemptively flip the
                    // writable bit on any existing file we're about
                    // to overwrite — harmless when it was already
                    // writable, recovers the common case when it
                    // wasn't.
                    if (canonicalTarget.exists() && !canonicalTarget.canWrite()) {
                        runCatching { canonicalTarget.setWritable(true) }
                    }

                    try {
                        canonicalTarget.outputStream().use { out ->
                            total += zin.copyTo(out)
                        }
                        // Best-effort mtime restore so extracted files
                        // keep their archive timestamps.
                        if (entry.time != -1L) canonicalTarget.setLastModified(entry.time)
                        fileCount++
                    } catch (t: Throwable) {
                        // Hard failure (file locked by another
                        // process, parent dir permissions, etc.).
                        // Collect and keep going — losing one file
                        // from a 10k-entry restore is a much better
                        // UX than aborting the whole operation.
                        skipped += SkippedEntry(
                            path = entry.name,
                            reason = t.message ?: t.javaClass.simpleName,
                        )
                    }
                }
                zin.closeEntry()
            }
        }

        return ExtractResult(
            fileCount = fileCount,
            directoryCount = dirCount,
            totalBytes = total,
            skipped = skipped.toList(),
        )
    }
}
