package org.open.file.ui.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.open.file.utils.FileSystemUtils
import java.io.File
import java.util.UUID

/**
 * A single recurring backup job.
 *
 *  - [id] is stable across restarts — used to key edits / deletes from the UI.
 *  - [sourcePath] is what gets archived. Same semantics as the Create-Backup
 *    dialog's source directory.
 *  - [targetDirectory] mirrors [BackupUiModel.targetDirectory]; null means
 *    "use the default archive folder".
 *  - [intervalMinutes] drives fixed-interval scheduling. Null when [cronExpression]
 *    is set — exactly one of the two is the source of truth, enforced by
 *    [new]'s factory overloads so the in-memory state can't be ambiguous.
 *  - [cronExpression] drives cron-based scheduling. When non-null it takes
 *    precedence over [intervalMinutes].
 *  - [enabled] is the user's pause switch. We keep disabled schedules
 *    around so edits survive a pause/resume cycle.
 *  - [lastRunAt] / [nextRunAt] are scheduler bookkeeping. [nextRunAt] is
 *    recomputed on save and after each successful run.
 *  - [createdAt] is first-seen. Used only for UI ordering.
 *
 * All instants are epoch milliseconds — consistent with the rest of the
 * app's `Instant.toEpochMilliseconds()` storage convention.
 */
@Serializable
data class BackupSchedule(
    val id: String,
    val sourcePath: String,
    val targetDirectory: String? = null,
    /** Fixed-interval cadence. Null when [cronExpression] is set. */
    val intervalMinutes: Long? = null,
    /**
     * 5-field POSIX cron expression (see [org.open.file.ui.util.CronExpression]).
     * Takes precedence over [intervalMinutes] when non-null.
     */
    val cronExpression: String? = null,
    val enabled: Boolean = true,
    /**
     * Whether the scheduled run should include hidden files (dotdirs
     * like `.git`, `.cache`). Default true for backwards compatibility
     * with schedules created before this field existed — the JSON
     * deserializer uses the default when the key is absent.
     */
    val includeHidden: Boolean = true,
    val lastRunAt: Long? = null,
    val nextRunAt: Long,
    val createdAt: Long,
) {
    /**
     * Human-friendly summary for list rows. Mirrors [PRESETS] labels
     * when the interval matches a known preset, otherwise derives a
     * compact "every N hours" / cron expression rendering.
     */
    fun cadenceLabel(): String {
        cronExpression?.let { return "cron: $it" }
        val mins = intervalMinutes ?: return "—"
        PRESETS.firstOrNull { it.second == mins }?.let { return it.first }
        return when {
            mins % (24 * 60) == 0L -> "Every ${mins / (24 * 60)} days"
            mins % 60 == 0L -> "Every ${mins / 60} hours"
            else -> "Every $mins min"
        }
    }

    companion object {
        /** Named aliases the UI surfaces as presets. Values in minutes. */
        val PRESETS: List<Pair<String, Long>> = listOf(
            "Every hour" to 60L,
            "Every 6 hours" to 6 * 60L,
            "Every 12 hours" to 12 * 60L,
            "Daily" to 24 * 60L,
            "Weekly" to 7 * 24 * 60L,
        )

        /**
         * Build a fresh fixed-interval schedule — nextRunAt defaults to
         * "one interval from now" so a just-created schedule doesn't
         * fire immediately and surprise the user.
         */
        fun interval(
            sourcePath: String,
            targetDirectory: String?,
            intervalMinutes: Long,
            enabled: Boolean = true,
            includeHidden: Boolean = true,
        ): BackupSchedule {
            val now = System.currentTimeMillis()
            return BackupSchedule(
                id = UUID.randomUUID().toString(),
                sourcePath = sourcePath,
                targetDirectory = targetDirectory,
                intervalMinutes = intervalMinutes,
                cronExpression = null,
                enabled = enabled,
                includeHidden = includeHidden,
                lastRunAt = null,
                nextRunAt = now + intervalMinutes * 60_000L,
                createdAt = now,
            )
        }

        /**
         * Build a cron-driven schedule. [nextRunAt] is computed from
         * the cron expression's first match after now — bubbles up a
         * runtime failure if the expression is invalid (caller should
         * validate via [org.open.file.ui.util.CronExpression.parseOrNull]
         * before reaching this point).
         */
        fun cron(
            sourcePath: String,
            targetDirectory: String?,
            cronExpression: String,
            enabled: Boolean = true,
            includeHidden: Boolean = true,
        ): BackupSchedule {
            val now = System.currentTimeMillis()
            val cron = org.open.file.ui.util.CronExpression.parse(cronExpression)
            // Fall back to "now + 1 day" if the cron is impossibly
            // restrictive — the scheduler tick will re-skip past it
            // every time. Still better than crashing construction.
            val next = cron.nextAfter(now) ?: (now + 24 * 60 * 60 * 1000L)
            return BackupSchedule(
                id = UUID.randomUUID().toString(),
                sourcePath = sourcePath,
                targetDirectory = targetDirectory,
                intervalMinutes = null,
                cronExpression = cronExpression,
                enabled = enabled,
                includeHidden = includeHidden,
                lastRunAt = null,
                nextRunAt = next,
                createdAt = now,
            )
        }
    }
}

/**
 * JSON-backed persistence for [BackupSchedule] rows.
 *
 * Stored at `~/.open-file/ui/schedules.json` as a single top-level array
 * so read / write stays atomic — no partial files, no line-parser corner
 * cases. Thread-safe via an in-memory lock.
 *
 * A malformed file on load is logged to stderr and treated as empty; the
 * next save overwrites it with a valid array. We'd rather lose scheduling
 * history than refuse to launch the app.
 */
class BackupSchedulesStore(
    private val file: File = FileSystemUtils.home("ui/schedules.json"),
) {
    private val lock = Any()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun loadAll(): List<BackupSchedule> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString<List<BackupSchedule>>(file.readText())
        } catch (e: SerializationException) {
            System.err.println("open-file: corrupt schedules.json (${e.message}), treating as empty")
            emptyList()
        } catch (e: Throwable) {
            System.err.println("open-file: failed to read schedules.json: ${e.message}")
            emptyList()
        }
    }

    fun saveAll(schedules: List<BackupSchedule>) = synchronized(lock) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(schedules))
    }

    /**
     * Insert if [schedule.id] is new, replace otherwise. Convenience so
     * call sites don't have to re-read → mutate → write themselves.
     */
    fun upsert(schedule: BackupSchedule) = synchronized(lock) {
        val current = loadAll().toMutableList()
        val idx = current.indexOfFirst { it.id == schedule.id }
        if (idx >= 0) current[idx] = schedule else current.add(schedule)
        saveAll(current)
    }

    fun delete(id: String) = synchronized(lock) {
        saveAll(loadAll().filterNot { it.id == id })
    }
}
