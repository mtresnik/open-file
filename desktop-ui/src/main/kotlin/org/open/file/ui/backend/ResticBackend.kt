package org.open.file.ui.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.open.file.ui.data.ResticPreferences
import org.open.file.ui.util.ToolExecutor
import java.io.File
import kotlin.time.Instant

/**
 * Browse-only restic adapter. Wraps the `restic` CLI and surfaces
 * snapshot metadata to the UI, without (yet) supporting create /
 * delete / restore — those can follow once the list view has proven
 * useful.
 *
 * Strategy:
 *  - Shell out through [ToolExecutor] (inherits the Windows `cmd /c`
 *    wrapping and timeout handling we already trust).
 *  - Ask restic for JSON output (`--json` is stable across the 0.1x
 *    line and trivial to deserialise).
 *  - Map each entry to a small [ResticSnapshot] record the caller
 *    can turn into a `BackupUiModel`.
 *
 * Errors are reported as thrown exceptions with the tail of restic's
 * stderr as the message, so the ErrorReporter toast reads usefully
 * ("wrong password", "repository does not exist", etc.) without the
 * caller having to know anything about restic's exit codes.
 */
class ResticBackend(
    private val prefs: ResticPreferences = ResticPreferences(),
) {

    /** Single snapshot as reported by `restic snapshots --json`. */
    @Serializable
    data class ResticSnapshot(
        val id: String,
        /** First 8 chars of [id] — restic's usual short form. */
        val short_id: String = "",
        /** RFC-3339 timestamp. restic emits this in UTC with nanosecond precision. */
        val time: String,
        /**
         * Source paths the snapshot covers. Usually one entry; restic
         * supports backing up multiple paths into a single snapshot
         * but that's uncommon.
         */
        val paths: List<String> = emptyList(),
        val hostname: String = "",
        val username: String = "",
        val tags: List<String> = emptyList(),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Fetch the list of snapshots in the configured repository. Returns
     * an empty list when no repo is configured; throws otherwise on
     * failure so the caller's try/catch routes the error through the
     * reporter.
     */
    fun listSnapshots(): List<ResticSnapshot> {
        val config = prefs.load()
        if (config.repoPath.isBlank()) return emptyList()

        val args = buildList {
            add("restic")
            add("-r")
            add(config.repoPath)
            config.passwordFile?.takeIf { it.isNotBlank() }?.let {
                add("--password-file")
                add(it)
            }
            // `--no-lock` — listing doesn't need an exclusive lock.
            // Useful when another restic process is running against
            // the same repo.
            add("--no-lock")
            add("snapshots")
            add("--json")
        }

        // ToolExecutor already handles PATH resolution (including
        // `cmd /c` on Windows) and redirects stderr into stdout.
        // Working directory doesn't matter for `restic snapshots`;
        // use the repo dir when local, otherwise the user home.
        val workingDir = File(config.repoPath).takeIf { it.isDirectory }
            ?: File(System.getProperty("user.home"))

        val result = ToolExecutor.run(args, workingDir, timeoutSeconds = 30)
        if (!result.success) {
            // Trim to the last couple of non-blank lines so the toast
            // surfaces restic's actual error ("wrong password", "Fatal:
            // unable to open config file", etc.) without us dumping
            // 50 lines of stack-trace-alike.
            val summary = result.output.lines()
                .filter { it.isNotBlank() }
                .takeLast(3)
                .joinToString("\n")
                .ifBlank { "restic snapshots failed (exit ${result.exitCode})" }
            error(summary)
        }
        // restic prints a single top-level JSON array. Extract it
        // tolerantly — some wrappers (e.g. scripts that prefix env
        // dumps) can put garbage before the payload.
        val payload = result.output
            .dropWhile { it != '[' }
            .ifBlank { "[]" }
        return json.decodeFromString(payload)
    }
}

/**
 * Parsed-out snapshot timestamp — restic's "time" field uses ISO-8601
 * nano precision. `kotlin.time.Instant.parse` handles it directly.
 */
fun ResticBackend.ResticSnapshot.createdAtInstant(): Instant = Instant.parse(time)
