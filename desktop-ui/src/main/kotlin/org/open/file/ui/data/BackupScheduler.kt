package org.open.file.ui.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * In-process scheduler that fires due [BackupSchedule]s by invoking
 * [fireBackup]. The caller owns the UI [CoroutineScope] we run on — the
 * scheduler stops when the scope is cancelled (which for a single-window
 * desktop app is "at exit").
 *
 * Design notes:
 *  - Fixed 30-second tick. Fine granularity for a desktop app (cheapest
 *    schedule is hourly — tick frequency is a rounding detail) while
 *    still giving "cancel a bad schedule before it fires" room.
 *  - [fireBackup] reuses the app's existing `maybeStartBackup` lambda so
 *    scheduled runs get the identical-backup pre-check, progress dialog,
 *    and auto-select behaviour for free.
 *  - [nextRunAt] is stamped forward *before* the backup starts so a slow
 *    backup can't double-fire on the next tick. Failures in the backup
 *    pipeline surface through the app's usual error reporter — no special
 *    retry logic; the next scheduled tick tries again.
 *  - We guard against clock jumps (sleep/wake): if [nextRunAt] is more
 *    than one interval in the past we snap it forward so a machine that
 *    was asleep overnight doesn't chain-fire every missed run all at
 *    once.
 */
class BackupScheduler(
    private val store: BackupSchedulesStore,
    /**
     * Called on the caller's scope when a schedule is due. Carries
     * the schedule's stored include-hidden choice so each run honours
     * the original creation-time toggle even if the default changes
     * in a future release.
     */
    private val fireBackup: (sourcePath: String, targetDirectory: String?, includeHidden: Boolean) -> Unit,
    /** Override for tests. Real callers don't need to set this. */
    private val now: () -> Long = { System.currentTimeMillis() },
    private val tickMillis: Long = 30_000L,
) {
    private var job: Job? = null

    /** Start the ticker on [scope]. Safe to call multiple times — no-op if already running. */
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                try {
                    tick()
                } catch (t: Throwable) {
                    // Don't let a single broken tick kill the whole
                    // scheduler loop — swallow, log, and keep going.
                    System.err.println("open-file: scheduler tick failed: ${t.message}")
                }
                delay(tickMillis)
            }
        }
    }

    /** Stop the ticker without cancelling the enclosing scope. */
    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * Process one scheduler tick: load schedules, pick those whose
     * `nextRunAt` has passed, advance their bookkeeping fields, persist
     * the updates, and dispatch the fires.
     *
     * Ordering is important here — we persist the advanced
     * [lastRunAt] / [nextRunAt] *before* dispatching the fire, so a
     * crash mid-fire doesn't double-schedule the same backup.
     */
    internal fun tick() {
        val t = now()
        val schedules = store.loadAll()
        val due = schedules.filter { it.enabled && it.nextRunAt <= t }
        if (due.isEmpty()) return

        val advanced = schedules.map { s ->
            if (!due.any { it.id == s.id }) return@map s
            s.copy(
                lastRunAt = t,
                nextRunAt = nextRunAfter(s, t),
            )
        }
        store.saveAll(advanced)

        // Dispatch after persistence so a runaway fire path can't get
        // re-triggered next tick because its nextRunAt never advanced.
        due.forEach { fireBackup(it.sourcePath, it.targetDirectory, it.includeHidden) }
    }

    /**
     * Compute the next scheduled instant after processing one at [processedAt].
     *
     *  - Cron branch: delegate to [CronExpression.nextAfter]. If the
     *    expression somehow fails to match within the scanner's 4-year
     *    cap, we park the schedule a day out so the UI has a chance to
     *    surface the problem to the user instead of hot-looping.
     *  - Interval branch: add the cadence, snapping forward to the
     *    first future boundary if the machine was asleep for multiple
     *    intervals. Prevents a laptop-woke-from-overnight-sleep burst
     *    of missed fires.
     */
    private fun nextRunAfter(schedule: BackupSchedule, processedAt: Long): Long {
        schedule.cronExpression?.let { expr ->
            val cron = runCatching {
                org.open.file.ui.util.CronExpression.parse(expr)
            }.getOrNull()
            val nextCron = cron?.nextAfter(processedAt)
            return nextCron ?: (processedAt + 24 * 60 * 60 * 1000L)
        }
        val intervalMs = (schedule.intervalMinutes ?: 60L) * 60_000L
        var next = (schedule.nextRunAt.takeIf { it > 0 } ?: processedAt) + intervalMs
        while (next <= processedAt) next += intervalMs
        return next
    }
}
