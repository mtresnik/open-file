package org.open.file.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Divider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.open.file.backup.gen.BackupArchiver
import org.open.file.snapshot.models.SavedSnapshot
import org.open.file.snapshot.store.domain.SnapshotNode
import org.open.file.ui.components.IdenticalBackupDialog
import org.open.file.ui.components.LanguageDialog
import org.open.file.ui.components.NavItem
import org.open.file.ui.components.ProgressDialog
import org.open.file.ui.components.SettingsDialog
import org.open.file.ui.components.Sidebar
import org.open.file.ui.backend.ResticBackend
import org.open.file.ui.backend.createdAtInstant
import org.open.file.ui.components.DuplicateScheduleDialog
import org.open.file.ui.components.ScheduleBackupDialog
import org.open.file.ui.data.AppBehaviorPreferences
import org.open.file.ui.data.BackupPreferences
import org.open.file.ui.data.BackupSchedule
import org.open.file.ui.data.BackupScheduler
import org.open.file.ui.data.BackupSchedulesStore
import org.open.file.ui.data.ResticPreferences
import org.open.file.ui.data.WindowPreferences
import org.open.file.ui.data.BackupRepository
import org.open.file.ui.data.LanguagePreferences
import org.open.file.ui.data.SnapshotRepository
import org.open.file.ui.data.TemplateRepository
import org.open.file.ui.data.ThemePreferences
import org.open.file.ui.data.toUiModel
import org.open.file.ui.i18n.LocalStrings
import org.open.file.ui.i18n.localeByCode
import org.open.file.ui.notifications.ErrorReporter
import org.open.file.ui.notifications.ToastHost
import org.open.file.ui.notifications.ToastLevel
import org.open.file.ui.notifications.rememberToaster
import org.open.file.ui.screens.*
import org.open.file.ui.state.Tab
import org.open.file.ui.state.rememberAppState
import org.open.file.ui.theme.AppColors
import org.open.file.ui.theme.AppTheme
import org.open.file.ui.util.LayoutMetrics
import org.open.file.ui.util.LocalLayoutMetrics
import org.open.file.ui.util.PathEquality
import org.open.file.ui.util.pathEquals
import org.open.file.ui.util.revealInFileExplorer
import java.io.File

// ──────────────────────────────────────────────────────────
// Wiring
//
// The UI calls the shared services directly via repositories that bounce
// every CRUD through a coroutine dispatcher — no HTTP layer, no second
// process. The concrete DAO implementation (SQL by default, Mongo behind a
// build.target flag) is picked up from the classpath via ServiceLoader at
// runtime; see desktop-ui/build.gradle.kts.
// ──────────────────────────────────────────────────────────

/**
 * In-flight "we think this might be a duplicate backup" state. When a
 * create-backup request triggers the identical-backup pre-check and
 * the pre-check fires, we stash the intended source path + the existing
 * backup's display info here and let [IdenticalBackupDialog] take over.
 */
// ──────────────────────────────────────────────
// Window-size floor
// ──────────────────────────────────────────────
//
// Hard minimum dp/logical-pixel bounds for the app window. Below
// these dimensions list rows clip badly and dialogs can't meet their
// own 480dp-width contract. Used in two places:
//   1. `window.minimumSize` — enforced by AWT so the user can't drag
//      smaller than this via the resize handle.
//   2. `validateWindowState` — applied to saved window state on load
//      so a prefs file written by an older (smaller) build can't
//      re-open the app below the floor.
private const val MIN_WINDOW_WIDTH_DP = 720
private const val MIN_WINDOW_HEIGHT_DP = 520

private data class PendingIdenticalBackup(
    val sourcePath: String,
    /** Target directory carried over so the confirm path uses the same placement. */
    val targetDirectory: String?,
    /** Include-hidden choice carried through so confirm respects the user's original toggle. */
    val includeHidden: Boolean,
    val existingName: String,
    val existingTimeAgo: String,
)

/**
 * In-flight "you're about to schedule a backup that already has a
 * schedule" state. Stashed when the [BackupsScreen.onCreateSchedule]
 * check finds a matching (sourcePath, targetDirectory) pair — and
 * surfaced via [DuplicateScheduleDialog]. Confirming the dialog
 * pops the pending spec off and routes it back through the normal
 * upsert path.
 */
private data class PendingDuplicateSchedule(
    val spec: org.open.file.ui.screens.ScheduleSpec,
    /** Display name of the source directory for the dialog message. */
    val sourceName: String,
    /** Cadence label of the *existing* schedule, shown to the user. */
    val existingCadence: String,
)

/**
 * Shared write path for both the "no duplicate, just insert" and the
 * "user confirmed the duplicate" branches. Extracted to a file-level
 * function rather than a lambda so both call sites stay identical —
 * easier than reaching for the same lambda from inside a pending-
 * state callback further down the composable.
 *
 * Routes the [ScheduleSpec] through the right [BackupSchedule]
 * factory, persists via [schedulesStore], mirrors the change into
 * the Compose list via [reloadSchedules], and confirms the write
 * with a toast.
 */
private fun insertSchedule(
    spec: org.open.file.ui.screens.ScheduleSpec,
    schedulesStore: BackupSchedulesStore,
    reloadSchedules: () -> Unit,
    reporter: ErrorReporter,
) {
    val fresh = when (spec) {
        is org.open.file.ui.screens.ScheduleSpec.Interval -> BackupSchedule.interval(
            sourcePath = spec.sourcePath,
            targetDirectory = spec.targetDirectory,
            intervalMinutes = spec.intervalMinutes,
            includeHidden = spec.includeHidden,
        )
        is org.open.file.ui.screens.ScheduleSpec.Cron -> try {
            BackupSchedule.cron(
                sourcePath = spec.sourcePath,
                targetDirectory = spec.targetDirectory,
                cronExpression = spec.expression,
                includeHidden = spec.includeHidden,
            )
        } catch (t: Throwable) {
            // Dialog validated before submit, but belt-and-braces in
            // case the parser threw on a value the validator let
            // through.
            reporter.reportError("Invalid cron expression", t)
            return
        }
    }
    schedulesStore.upsert(fresh)
    reloadSchedules()
    // Surface confirmation so the user knows the row landed in the
    // Schedules list (out-of-sight from the dialog that just closed).
    reporter.report(
        "Schedule added: ${fresh.cadenceLabel()} — ${fresh.sourcePath}",
        org.open.file.ui.notifications.ToastLevel.INFO,
    )
}

/**
 * Map a restic snapshot into the shared `BackupUiModel`. Loses some
 * restic-specific info (hostname, tags, tree id) — fine for the
 * browse-only view; can be surfaced in a detail pane later.
 *
 *  - `rootPath` = restic's first `paths` entry; virtually all snapshots
 *    have a single source path.
 *  - Size fields stay zero because `restic snapshots` doesn't include
 *    them; we'd need a per-id `restic stats` call to get bytes, which
 *    is expensive and would stall the list.
 *  - `entryCount` similarly unknown.
 *  - Duplicate-detection pre-check keys off `originalSizeBytes`, which
 *    is 0 here — the pre-check compares exact byte totals, so a restic
 *    row with 0 bytes will never falsely match a native row with real
 *    bytes. No special-casing needed.
 */
private fun org.open.file.ui.backend.ResticBackend.ResticSnapshot.toBackupUiModel(): org.open.file.ui.screens.BackupUiModel {
    val firstPath = paths.firstOrNull().orEmpty()
    val displayName = java.io.File(firstPath).name.ifBlank { firstPath.ifBlank { short_id } }
    return org.open.file.ui.screens.BackupUiModel(
        // Prefix so restic ids never collide with native UUIDs and
        // future write paths can pattern-match by prefix to pick the
        // right backend.
        id = "restic:${id}",
        name = displayName,
        rootPath = firstPath,
        snapshotIds = emptyList(),
        // Reuse the restic repo path as the "destination" — that's
        // where the archive content physically lives, even if the
        // archive file itself is a chunked object store.
        destination = "${short_id.ifBlank { id.take(8) }} @ ${paths.firstOrNull() ?: ""}",
        createdAt = createdAtInstant(),
        size = "—",
        status = org.open.file.ui.screens.BackupStatus.COMPLETED,
        compression = "restic",
        backend = org.open.file.ui.screens.BackupBackend.RESTIC,
    )
}

fun main() = application {
    // Allow resizing down to a genuinely small window so the responsive
    // layout has something to adapt to — the classifyWidth/LayoutMetrics
    // breakpoints kick in around 780dp.
    val windowPrefs = remember { WindowPreferences() }
    // Load persisted state once on launch, then validate against the
    // currently-connected monitors. Common failure modes we want to
    // catch:
    //   - User unplugged the external monitor the window used to sit
    //     on; the saved Absolute position is now off every screen.
    //   - Screen resolution dropped (docked → laptop); the saved
    //     1920×1200 window no longer fits a 1366×768 panel.
    //   - Monitor was removed entirely (single → zero screens, e.g.
    //     remote session). Falling back to the platform default is
    //     always safe here.
    // When any of those trip, we drop the offending field back to
    // the built-in default rather than handing Compose a value that
    // would open the window unreachable or off-screen.
    val savedWindow = remember { windowPrefs.load() }
    val safeWindow = remember(savedWindow) { validateWindowState(savedWindow) }

    val initialSize = DpSize(
        safeWindow.width ?: 1200.dp,
        safeWindow.height ?: 780.dp,
    )
    val initialPosition = if (safeWindow.x != null && safeWindow.y != null) {
        WindowPosition.Absolute(safeWindow.x, safeWindow.y)
    } else {
        WindowPosition.PlatformDefault
    }
    val windowState = rememberWindowState(
        size = initialSize,
        position = initialPosition,
        placement = safeWindow.placement ?: androidx.compose.ui.window.WindowPlacement.Floating,
    )

    // Persist window size / position / placement whenever they change.
    // Debounced so dragging the window or live-resizing doesn't cause
    // a write on every pixel — 500ms lands a single write once the
    // gesture stops, and the subsequent flow.first value captures
    // the final state.
    LaunchedEffect(windowState) {
        snapshotFlow {
            Triple(windowState.size, windowState.position, windowState.placement)
        }.debounce(500).collect { (size, position, placement) ->
            val abs = position as? WindowPosition.Absolute
            windowPrefs.save(
                width = size.width,
                height = size.height,
                x = abs?.x,
                y = abs?.y,
                placement = placement,
            )
        }
    }

    // Built once per process. Construction itself is cheap; the services
    // they wrap are lazy so ServiceLoader lookups don't run until the first
    // call — any classpath misconfig surfaces in the per-call error handler
    // below instead of crashing the whole window.
    val snapshotRepo = remember { SnapshotRepository() }
    val templateRepo = remember { TemplateRepository() }
    val backupRepo = remember { BackupRepository() }
    val themePrefs = remember { ThemePreferences() }
    val languagePrefs = remember { LanguagePreferences() }
    val backupPrefs = remember { BackupPreferences() }
    val appBehaviorPrefs = remember { AppBehaviorPreferences() }
    val resticPrefs = remember { ResticPreferences() }
    val resticBackend = remember { ResticBackend(resticPrefs) }

    // Load-and-apply the persisted theme before the first frame renders.
    // Running here (rather than in LaunchedEffect) means the initial paint
    // uses the saved colours — no flash of the default theme.
    remember { themePrefs.loadAndApply() }

    // Same deal for language: resolve the persisted code up-front so the
    // CompositionLocal below is seeded with the correct Strings instance.
    val initialLocale = remember { localeByCode(languagePrefs.load()) }
    var currentLocale by remember { mutableStateOf(initialLocale) }

    // Window-visibility flag gated by the tray: closing the window
    // flips this to false (window hides, scheduler keeps running);
    // the tray's Show item flips it back. Quit from the tray is the
    // only path that actually exits the process.
    var windowVisible by remember { mutableStateOf(true) }

    // Live-read the tray preference so toggling it in Settings takes
    // effect immediately — the next close-button click picks up the
    // new behaviour without a restart.
    var minimizeToTray by remember { mutableStateOf(appBehaviorPrefs.isMinimizeToTrayEnabled()) }

    // System tray integration — lets the app idle in the background
    // while scheduled backups still fire. Only mounted when the user
    // has opted into minimize-to-tray; flipping the toggle off in
    // Settings removes the tray icon next recomposition so quit-on-
    // close users don't get a stray icon in their system tray.
    //
    // rememberVectorPainter renders a Material icon into a Painter
    // the tray can use; avoids shipping a separate .png asset.
    if (minimizeToTray) {
        Tray(
            // Tinted folder painter — stock vector painters render
            // black on the tray, which vanishes on dark Windows /
            // macOS chrome. See AppIcon.kt for why we wrap rather
            // than set the tint inline.
            icon = org.open.file.ui.util.rememberAppIconPainter(),
            tooltip = "OpenFile — running in background",
            // Clicking the tray icon itself toggles visibility, same
            // as the "Show" menu item; feels natural on Windows where
            // the standard behaviour is single-click-to-restore.
            onAction = { windowVisible = true },
            menu = {
                Item("Show window", onClick = { windowVisible = true })
                Separator()
                Item("Quit", onClick = ::exitApplication)
            },
        )
    }

    Window(
        // Close-button behaviour depends on the tray setting:
        //   - minimizeToTray = true: hide the window; tray keeps the
        //     scheduler running in the background.
        //   - minimizeToTray = false: exit the application — the
        //     classic quit-on-close contract for users who don't
        //     want a background process.
        onCloseRequest = {
            if (minimizeToTray) {
                windowVisible = false
            } else {
                exitApplication()
            }
        },
        visible = windowVisible,
        title = "OpenFile",
        // Window / taskbar / Alt-Tab icon. Without this the IDE run
        // (and the un-packaged JAR) shows the default JDK coffee-cup
        // because AWT falls back to its own icon when the Compose
        // Window doesn't set one. jpackage'd builds override this
        // with the bundled .ico / .icns / .png, but IDE runs never
        // hit that path — so we set a tinted Folder painter here to
        // keep every code path consistent with the sidebar logo and
        // tray icon. Tint is applied because stock vector painters
        // render black, which is invisible against dark taskbars.
        icon = org.open.file.ui.util.rememberAppIconPainter(),
        state = windowState,
    ) {
        // Enforce a usable minimum size at the AWT layer so the user
        // physically can't drag the window below it. Compose's
        // responsive layout already reshapes the sidebar / header at
        // compact widths, but below ~720×520 list rows start getting
        // squeezed to unreadable widths and dialog content can't
        // meet the 480dp dialog width + padding budget.
        //
        // AWT's `minimumSize` is in *logical* pixels (1 logical px =
        // 1 dp on a standard-density display; the OS scales up for
        // hi-DPI on its own), so we pass the dp values straight
        // through — no density math needed.
        LaunchedEffect(window) {
            window.minimumSize = java.awt.Dimension(
                MIN_WINDOW_WIDTH_DP,
                MIN_WINDOW_HEIGHT_DP,
            )
        }
        AppTheme {
            CompositionLocalProvider(LocalStrings provides currentLocale.strings) {
                App(
                    snapshotRepo = snapshotRepo,
                    templateRepo = templateRepo,
                    backupRepo = backupRepo,
                    themePrefs = themePrefs,
                    backupPrefs = backupPrefs,
                    resticPrefs = resticPrefs,
                    resticBackend = resticBackend,
                    currentLocaleCode = currentLocale.code,
                    minimizeToTray = minimizeToTray,
                    onMinimizeToTrayChanged = { enabled ->
                        // Mirror into the outer state so the
                        // conditional Tray + close handler pick up
                        // the change next recomposition, then
                        // persist so the choice survives restarts.
                        minimizeToTray = enabled
                        appBehaviorPrefs.setMinimizeToTray(enabled)
                    },
                    onLocaleSelected = { newLocale ->
                        currentLocale = newLocale
                        languagePrefs.save(newLocale.code)
                    },
                )
            }
        }
    }
}

@Composable
fun App(
    snapshotRepo: SnapshotRepository,
    templateRepo: TemplateRepository,
    backupRepo: BackupRepository,
    themePrefs: ThemePreferences,
    backupPrefs: BackupPreferences,
    resticPrefs: ResticPreferences,
    resticBackend: ResticBackend,
    currentLocaleCode: String,
    /** Current minimize-to-tray preference, read live from [main] 's state. */
    minimizeToTray: Boolean,
    /** Fires when the user toggles minimize-to-tray in Settings. */
    onMinimizeToTrayChanged: (Boolean) -> Unit,
    onLocaleSelected: (org.open.file.ui.i18n.Locale) -> Unit,
) {
    val state = rememberAppState()
    // Scope tied to the composition. Any in-flight CRUD is cancelled if App
    // ever leaves composition (which for a single-window desktop app only
    // happens at exit).
    val scope = rememberCoroutineScope()

    // Central funnel for user-visible errors: toast at the bottom of the
    // screen + append to ~/.open-file/logs/error.log. Every catch in this
    // file now routes through `reporter` so users see failures and
    // maintainers have a breadcrumb trail to reproduce bug reports.
    val toaster = rememberToaster()
    val reporter = remember(toaster) { ErrorReporter(toaster) }

    val snapshots = remember { mutableStateListOf<SavedSnapshot>() }
    val rootNodes = remember { mutableStateMapOf<String, SnapshotNode>() }
    val templates = remember { mutableStateListOf<TemplateUiModel>() }
    val backups = remember { mutableStateListOf<BackupUiModel>() }
    // Backup schedules mirror the JSON store — kept in a Compose state
    // list so the Schedules dialog re-renders on add / toggle / delete
    // without having to re-read the file.
    val schedules = remember { mutableStateListOf<BackupSchedule>() }
    var showSchedules by remember { mutableStateOf(false) }

    // Progress state for the interruptible backup dialog. Kept at this
    // level (not inside BackupsScreen) because the Job driving the backup
    // is launched here — cancel has to reach the same Job, and it's
    // simplest to keep the observable progress next to it.
    var backupProgress by remember { mutableStateOf<BackupArchiver.Progress?>(null) }

    // In-flight scaffold state. Kept here at the App level (not in
    // TemplatesScreen) because scaffold can take a minute for
    // network-bound templates like `npm create vite`, and we want
    // the progress modal to persist even if the user navigates
    // away from the Templates tab mid-scaffold. Carries the
    // template's display name + Job so the modal can render a
    // descriptive message and Cancel can cooperatively interrupt
    // the subprocess.
    data class ScaffoldProgress(val templateName: String, val projectName: String)
    var scaffoldProgress by remember { mutableStateOf<ScaffoldProgress?>(null) }
    var scaffoldJob by remember { mutableStateOf<Job?>(null) }
    var backupJob by remember { mutableStateOf<Job?>(null) }

    // Pre-create pause state: when the identical-backup pre-check fires,
    // we stash the pending source path + the existing match here and
    // surface a confirmation dialog. User confirms → we fall through to
    // the normal backup creation; cancels → we drop the state and no
    // backup runs.
    var warnIdenticalBackups by remember { mutableStateOf(backupPrefs.isWarnIdenticalEnabled()) }
    // Global default target directory pref — pre-fills the Create
    // Backup dialog's Target field so users who always write to the
    // same external drive don't have to retype / re-pick the path.
    // Null / blank means no preference and the dialog stays empty.
    var defaultBackupTargetDir by remember { mutableStateOf(backupPrefs.getDefaultTargetDirectory().orEmpty()) }
    var pendingIdenticalBackup by remember { mutableStateOf<PendingIdenticalBackup?>(null) }
    // Same-shape pending state for the duplicate-schedule confirmation.
    // No settings toggle yet — scheduling is low-frequency enough that
    // always-warn feels right; easy to promote to a pref later if
    // feedback asks for it.
    var pendingDuplicateSchedule by remember { mutableStateOf<PendingDuplicateSchedule?>(null) }

    // Restic config mirrored into Compose state so the Settings dialog
    // edits re-render the text fields as the user types. Changes write
    // through to the prefs file via onResticConfigChanged below.
    var resticRepoPath by remember { mutableStateOf(resticPrefs.load().repoPath) }
    var resticPasswordFile by remember { mutableStateOf(resticPrefs.load().passwordFile.orEmpty()) }

    /**
     * Rebuild the backups list from both backends. Native first (fast,
     * local DB read) so the user sees something immediately; restic
     * rows are appended after the shell-out returns.
     *
     * Failures from either side are reported via the toast but don't
     * stop the other side from rendering — a misconfigured restic repo
     * shouldn't nuke the native list.
     */
    val reloadBackups: suspend () -> Unit = reload@{
        try {
            val native = backupRepo.listAll().map { it.toUiModel() }
            val restic = if (resticPrefs.isConfigured()) {
                try {
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        resticBackend.listSnapshots().map { it.toBackupUiModel() }
                    }
                } catch (t: Throwable) {
                    reporter.reportError("Load restic snapshots failed", t)
                    emptyList()
                }
            } else emptyList()

            // Merge chronologically — newest first. Users expect
            // recent activity to float regardless of which backend
            // produced it.
            val merged = (native + restic).sortedByDescending { it.createdAt }
            backups.clear()
            backups.addAll(merged)
        } catch (t: Throwable) {
            reporter.reportError("Failed to load backups", t)
        }
    }

    // Persistent schedules store + in-memory sync helper. `reloadSchedules`
    // is the single place UI callbacks go through after a create/delete/
    // toggle so the Compose list stays in lockstep with the JSON file.
    val schedulesStore = remember { BackupSchedulesStore() }
    val reloadSchedules: () -> Unit = {
        val loaded = schedulesStore.loadAll().sortedByDescending { it.createdAt }
        schedules.clear()
        schedules.addAll(loaded)
    }

    /**
     * Actual backup kickoff — launches the archive Job and drives the
     * progress dialog. Extracted into a local lambda so every entry
     * point (Create dialog, "new from same source", scheduled tick,
     * identical-backup confirm path) can reuse a single code path.
     *
     * [targetDirectory] is forwarded to the repo as the archive's
     * parent folder. null means "fall back to the default archive dir".
     *
     * [silent] is the scheduler's path: skip the progress modal, skip
     * auto-selecting the finished backup, and emit a toast on
     * completion instead. Scheduled runs happen in the background and
     * interrupting the user's current view with a modal + tab switch
     * is invasive. Manual runs still get the full foreground UX.
     */
    val startBackup: (String, String?, Boolean, Boolean) -> Unit = { sourcePath, targetDirectory, silent, includeHidden ->
        if (!silent) {
            backupProgress = BackupArchiver.Progress(
                phase = BackupArchiver.Phase.SCANNING,
                filesProcessed = 0,
                totalFiles = 0,
                currentFile = sourcePath,
            )
        }
        val job = scope.launch {
            try {
                val created = backupRepo.create(
                    sourcePath = sourcePath,
                    targetDirectory = targetDirectory,
                    includeHidden = includeHidden,
                    onProgress = { p ->
                        // Only drive the progress state when we're
                        // the foreground-facing path; silent runs
                        // drop the updates on the floor.
                        if (!silent) backupProgress = p
                    },
                ) ?: return@launch
                backups.add(0, created.toUiModel())
                created.snapshotId?.let { sid ->
                    snapshotRepo.getTree(sid)?.let { tree -> rootNodes[sid] = tree }
                    snapshotRepo.listAll()
                        .firstOrNull { it.id == sid }
                        ?.let { snapshots.add(0, it) }
                }
                if (silent) {
                    // Scheduler path: toast-only confirmation. Uses
                    // the source name (not the full path) for
                    // readability in the corner-pop.
                    val name = java.io.File(sourcePath).name.ifBlank { sourcePath }
                    reporter.report(
                        "Scheduled backup completed: $name",
                        org.open.file.ui.notifications.ToastLevel.INFO,
                    )
                } else {
                    state.selectBackup(created.id)
                }
            } catch (_: CancellationException) {
                // User-initiated cancel — archiver already cleaned up
                // the partial zip before re-throwing.
            } catch (t: Throwable) {
                reporter.reportError(
                    if (silent) "Scheduled backup failed" else "Create backup failed",
                    t,
                )
            } finally {
                if (!silent) {
                    backupProgress = null
                    backupJob = null
                }
            }
        }
        // Foreground jobs get their handle stored so the Cancel
        // button in the progress dialog can reach them. Scheduled
        // jobs run to completion without a user-facing cancel, so
        // we don't need to expose the job handle.
        if (!silent) backupJob = job
    }

    /**
     * Pre-check for accidental duplicate backups. Does a cheap scan
     * (file count + total bytes, no hashing) and compares against the
     * most recent backup of the same rootPath. On match, stashes the
     * info in [pendingIdenticalBackup] so the confirmation dialog
     * renders; otherwise falls through to [startBackup] immediately.
     *
     * Walk + stat runs on IO so we don't block the EDT for large
     * directories, then resumes on Main to mutate Compose state.
     */
    val maybeStartBackup: (String, String?, Boolean, Boolean) -> Unit = { sourcePath, targetDirectory, silent, includeHidden ->
        if (!warnIdenticalBackups) {
            startBackup(sourcePath, targetDirectory, silent, includeHidden)
        } else {
            scope.launch {
                val existing = backups
                    .filter { it.rootPath.pathEquals(sourcePath) }
                    .maxByOrNull { it.createdAt }
                if (existing == null) {
                    startBackup(sourcePath, targetDirectory, silent, includeHidden)
                    return@launch
                }
                val (fileCount, totalBytes) = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val dir = java.io.File(sourcePath)
                    if (!dir.exists() || !dir.isDirectory) return@withContext 0 to -1L
                    var count = 0
                    var total = 0L
                    // walkTopDown is fast — single pass, no hashing,
                    // no follow-symlinks-into-loop danger for our use.
                    //
                    // Apply the same hidden-file filter the archiver
                    // will — otherwise this pre-check counts `.git/`
                    // bytes that the actual archive would exclude,
                    // and the total never matches the stored
                    // `originalSizeBytes` of an existing include-
                    // hidden-off backup. Replay would then always
                    // fall through to startBackup instead of
                    // surfacing the identical-backup modal.
                    //
                    // `onEnter` returns false to prune subtrees so
                    // dotdirs aren't descended into when
                    // !includeHidden — matches the archiver's filter
                    // exactly (see BackupArchiver.includeEntry).
                    val walk = dir.walkTopDown().onEnter { d ->
                        includeHidden || (!d.isHidden && !d.name.startsWith("."))
                    }
                    walk.forEach { f ->
                        if (f.isFile) {
                            if (!includeHidden && (f.isHidden || f.name.startsWith("."))) return@forEach
                            count++
                            total += f.length()
                        }
                    }
                    count to total
                }
                // entryCount in the saved model includes directories;
                // our walk counts only files, so compare to the file
                // subset. Total bytes are apples-to-apples with
                // originalSizeBytes. A dual-match is a very strong
                // "nothing changed" signal.
                if (totalBytes >= 0 && totalBytes == existing.originalSizeBytes) {
                    // Scheduler-fired runs skip the identical-backup
                    // dialog entirely — interrupting the user with a
                    // modal for a background job they didn't initiate
                    // is worse than the minor cost of a duplicate
                    // archive. A future pref could expose a "warn for
                    // scheduled too" toggle, but the default stays
                    // "silent means silent".
                    if (silent) {
                        startBackup(sourcePath, targetDirectory, silent, includeHidden)
                    } else {
                        pendingIdenticalBackup = PendingIdenticalBackup(
                            sourcePath = sourcePath,
                            targetDirectory = targetDirectory,
                            includeHidden = includeHidden,
                            existingName = existing.name,
                            existingTimeAgo = timeAgo(existing.createdAt),
                        )
                    }
                } else {
                    startBackup(sourcePath, targetDirectory, silent, includeHidden)
                }
            }
        }
    }

    // Extracted refresh action — same behaviour whether the user clicks
    // the manual Refresh chip in the detail pane or the window's
    // focus-regain triggers it. Clears the version-detection cache,
    // re-runs detection, and rebuilds the UI template list.
    val refreshTemplateVersions: () -> Unit = {
        scope.launch {
            try {
                org.open.file.ui.util.VersionDetector.clearCache()
                val loaded = templateRepo.listAllForUi()
                templates.clear()
                templates.addAll(loaded)
            } catch (t: Throwable) {
                reporter.reportError("Refresh versions failed", t)
            }
        }
    }

    // Auto-refresh on window focus-gained. The user's most common
    // reason for alt-tabbing away is to install a new toolchain, so
    // coming back to the app is the natural moment to re-detect.
    // `snapshotFlow` converts the Compose-observable focus state into
    // a cold Flow — `drop(1)` skips the initial composition read (the
    // app's startup load already ran detection once), and `filter { it }`
    // narrows the stream to focus-gained events only (we don't need to
    // do anything on focus-lost).
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(Unit) {
        snapshotFlow { windowInfo.isWindowFocused }
            .drop(1)
            .filter { it }
            .collect {
                refreshTemplateVersions()
                // Re-pull restic snapshots too — if the user ran
                // `restic backup` from a terminal while alt-tabbed,
                // the new snapshot shows up when they return.
                reloadBackups()
            }
    }

    // Initial load. Each block is isolated so a failure loading templates
    // doesn't also wipe out snapshots (and vice versa). Errors surface
    // through the reporter — user sees a toast, log captures the trace.
    LaunchedEffect(Unit) {
        try {
            val loaded = snapshotRepo.listAll()
            snapshots.clear()
            snapshots.addAll(loaded)
            val trees = snapshotRepo.listTrees(loaded.map { it.id })
            rootNodes.clear()
            rootNodes.putAll(trees)
        } catch (t: Throwable) {
            reporter.reportError("Failed to load snapshots", t)
        }

        try {
            // Fast path — no tool-detection shellouts, so the list
            // appears immediately. Each packaged template's
            // `detectedToolVersions` is empty for now; the background
            // pass below fills them in and swaps the rows in place.
            val loaded = templateRepo.listAllForUiFast()
            templates.clear()
            templates.addAll(loaded)
        } catch (t: Throwable) {
            reporter.reportError("Failed to load templates", t)
        }

        reloadBackups()

        // Detection pass — runs after the first render so the initial
        // frame doesn't block on ~20 sequential `--version` shellouts.
        // Errors here are non-fatal: if a tool's detection fails the
        // row keeps its empty `detectedToolVersions` map and surfaces
        // "Not detected" until the user hits Refresh.
        launch {
            runCatching {
                val detectedById = templateRepo.refreshDetectedVersions(templates.toList())
                // Swap each row with the filled-in detection map in
                // place. Compose's SnapshotStateList picks up the
                // copy and re-renders only the affected rows.
                detectedById.forEach { (id, detected) ->
                    val idx = templates.indexOfFirst { it.id == id }
                    if (idx >= 0) {
                        val current = templates[idx]
                        templates[idx] = current.copy(
                            detectedToolVersions = detected,
                            // Preserve any user-chosen version, but
                            // backfill defaults for tools that had no
                            // prior selection (empty string).
                            selectedToolVersions = current.selectedToolVersions.mapValues { (tool, existing) ->
                                if (existing.isNotBlank()) existing
                                else detected[tool]?.firstOrNull().orEmpty()
                            },
                        )
                    }
                }
            }.onFailure { reporter.reportError("Tool detection failed", it) }
        }
        // Hydrate the in-memory schedule list from disk. Any malformed
        // file is logged inside the store and reads as empty — the
        // Schedules dialog shows its empty state in that case.
        reloadSchedules()
    }

    // Scheduler loop — ticks every 30s, fires due schedules through the
    // same maybeStartBackup path as a manual create. Started after the
    // initial reloadSchedules() inside a separate LaunchedEffect so the
    // Unit-keyed block above stays a one-shot load. Bound to `scope`
    // (the rememberCoroutineScope at App-level) so the loop cancels
    // when composition leaves — i.e. app exit.
    val backupScheduler = remember(schedulesStore) {
        BackupScheduler(
            store = schedulesStore,
            fireBackup = { source, target, includeHidden ->
                // silent = true: scheduler runs should never pop the
                // progress modal or auto-select the new backup.
                // maybeStartBackup forwards the flag so the identical-
                // backup dialog is also suppressed for scheduled runs.
                // includeHidden comes straight off the persisted
                // schedule so every run honours the user's original
                // toggle, even if the defaults shift in a later
                // release.
                maybeStartBackup(source, target, /* silent = */ true, includeHidden)
            },
        )
    }
    LaunchedEffect(backupScheduler) {
        backupScheduler.start(this)
    }

    val navItems = listOf(
        NavItem(Tab.SNAPSHOTS, Icons.Default.Inventory2, snapshots.size),
        NavItem(Tab.TEMPLATES, Icons.Default.GridView, templates.size),
        NavItem(Tab.BACKUPS, Icons.Default.Archive, backups.size),
    )

    // Snapshot id → backup id for every snapshot pinned by a backup.
    // Used by the snapshots screen to (a) draw a lock icon on affected
    // rows, (b) disable Delete in the detail pane, (c) offer a clickable
    // shortcut to the owning backup. Recomputed whenever `backups`
    // changes, which Compose tracks automatically via the state list.
    val snapshotToBackup = remember(backups.toList()) {
        backups.flatMap { bk -> bk.snapshotIds.map { it to bk.id } }.toMap()
    }

    // Settings + Language dialog visibility. The Theme controls live
    // inside SettingsDialog now (single umbrella) so there's no longer
    // a standalone Theme dialog trigger in the sidebar.
    var showSettings by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var paletteName by remember { mutableStateOf(themePrefs.loadPaletteName()) }
    var accentName by remember { mutableStateOf(themePrefs.loadAccentName()) }
    var fontChoice by remember { mutableStateOf(themePrefs.loadFontChoice()) }

    // Run-on-startup state is read directly from the OS at composition
    // time — no prefs file. The OS layer is the source of truth, so
    // toggling externally (e.g. the user removing the entry from Task
    // Manager → Startup) would be reflected on the next settings-open.
    var autostartStatus by remember { mutableStateOf(org.open.file.ui.util.AutostartManager.status()) }

    // BoxWithConstraints reads the composition's actual pixel size and
    // classifies it into a breakpoint. We provide LayoutMetrics through
    // a CompositionLocal so any composable deeper in the tree (Sidebar,
    // dialogs, detail panels) can adapt without having the dimensions
    // plumbed through every call site.
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(AppColors.background)) {
        val widthDp = with(density) { constraints.maxWidth.toDp() }
        val metrics = LayoutMetrics.forWidth(widthDp)

        CompositionLocalProvider(LocalLayoutMetrics provides metrics) {
            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(
                    state = state,
                    navItems = navItems,
                    onOpenSettings = { showSettings = true },
                    onOpenLanguage = { showLanguage = true },
                )
                Divider(
                    color = AppColors.border,
                    modifier = Modifier.fillMaxHeight().width(1.dp),
                )

        when (state.activeTab) {
            Tab.SNAPSHOTS -> SnapshotsScreen(
                state = state,
                snapshots = snapshots,
                rootNodes = rootNodes,
                snapshotToBackup = snapshotToBackup,
                onNavigateToBackup = { bid -> state.navigateToBackup(bid) },
                onCreateSnapshot = { rootPath ->
                    // scope.launch uses the composition's main dispatcher
                    // (AWT EDT). The repo's internal withContext(IO) bounces
                    // disk / DB work off-thread and resumes here on the EDT,
                    // so the state mutations below are thread-safe.
                    scope.launch {
                        try {
                            val created = snapshotRepo.create(rootPath) ?: return@launch
                            snapshots.add(0, created.snapshot)
                            rootNodes[created.snapshot.id] = created.tree
                            // Open the detail pane on the new row so the
                            // user confirms the create landed without
                            // having to hunt for it in the list.
                            state.selectSnapshot(created.snapshot.id)
                        } catch (t: Throwable) {
                            reporter.reportError("Create snapshot failed", t)
                        }
                    }
                },
                onDeleteSnapshot = { id ->
                    // Delete the DB rows first and only mutate UI state if
                    // that succeeds, so a failed delete doesn't leave the
                    // list out of sync with what's on disk.
                    scope.launch {
                        try {
                            val ok = snapshotRepo.delete(id)
                            if (ok) {
                                snapshots.removeAll { it.id == id }
                                rootNodes.remove(id)
                                if (state.selectedSnapshotId == id) state.selectSnapshot(null)
                            }
                        } catch (t: Throwable) {
                            reporter.reportError("Delete snapshot failed", t)
                        }
                    }
                },
                onDeleteSnapshotGroup = { rootPath ->
                    // Bulk delete — iterates the group's unlocked
                    // snapshots through the same single-delete pipeline
                    // so DB rows + root-node cache cleanup run
                    // identically per row. Locked rows (snapshots a
                    // backup pins) are skipped; the dialog's preview
                    // already hid them so the user's confirmation was
                    // scoped to this same set.
                    //
                    // Snapshot of the target ids up-front so we iterate
                    // over a stable set even as `snapshots` shrinks
                    // between rounds.
                    val lockedIds = snapshotToBackup.keys
                    val targets = snapshots
                        .filter { it.rootPath.pathEquals(rootPath) && it.id !in lockedIds }
                        .map { it.id }
                    if (targets.isEmpty()) {
                        // Shouldn't happen — the UI hides the button
                        // when there's nothing to delete — but defend
                        // against races between the confirm click and a
                        // snapshot being pinned by a new backup.
                        return@SnapshotsScreen
                    }
                    scope.launch {
                        try {
                            for (id in targets) {
                                val ok = snapshotRepo.delete(id)
                                if (ok) {
                                    snapshots.removeAll { it.id == id }
                                    rootNodes.remove(id)
                                    if (state.selectedSnapshotId == id) state.selectSnapshot(null)
                                }
                            }
                            // If the user was viewing this group's
                            // history pane, clear the selection so the
                            // empty pane renders (rather than hanging
                            // on a stale rootPath with no rows).
                            if (state.selectedSnapshotGroupPath?.pathEquals(rootPath) == true) {
                                state.selectSnapshotGroup(null)
                            }
                        } catch (t: Throwable) {
                            reporter.reportError("Delete snapshot group failed", t)
                        }
                    }
                },
            )
            Tab.TEMPLATES -> TemplatesScreen(
                state = state,
                templates = templates,
                onCreateTemplate = { name, desc, rootDir, icon, tags, baseId ->
                    scope.launch {
                        try {
                            val (created, storedIcon) = templateRepo.create(
                                name = name,
                                description = desc,
                                rootDir = rootDir,
                                icon = icon,
                                tags = tags,
                                baseTemplateId = baseId,
                            ) ?: return@launch
                            templates.add(0, created.toUiModel(storedIcon)
                                .copy(baseTemplateId = baseId))
                            state.selectTemplate(created.id.toString())
                        } catch (t: Throwable) {
                            reporter.reportError("Create template failed", t)
                        }
                    }
                },
                onCreateTemplateFromGit = { name, desc, gitUrl, icon, tags, baseId ->
                    scope.launch {
                        try {
                            val (created, storedIcon) = templateRepo.createFromGit(
                                gitUrl = gitUrl,
                                name = name,
                                description = desc,
                                icon = icon,
                                tags = tags,
                                baseTemplateId = baseId,
                            ) ?: return@launch
                            templates.add(0, created.toUiModel(storedIcon)
                                .copy(baseTemplateId = baseId))
                            state.selectTemplate(created.id.toString())
                        } catch (t: Throwable) {
                            reporter.reportError("Clone template failed", t)
                        }
                    }
                },
                onDeleteTemplate = { id ->
                    // Delete the DB row + icon pref first, only mutate UI
                    // state on success so a failure leaves the list in sync
                    // with what's actually persisted.
                    scope.launch {
                        try {
                            val ok = templateRepo.delete(id)
                            if (ok) {
                                templates.removeAll { it.id == id }
                                if (state.selectedTemplateId == id) state.selectTemplate(null)
                            }
                        } catch (t: Throwable) {
                            reporter.reportError("Delete template failed", t)
                        }
                    }
                },
                onSelectToolVersion = { templateId, tool, version ->
                    // Persist the choice and mirror it into the in-memory
                    // list so the detail pane reflects it without needing
                    // a full reload.
                    scope.launch {
                        try {
                            templateRepo.setToolVersion(templateId, tool, version)
                            val idx = templates.indexOfFirst { it.id == templateId }
                            if (idx >= 0) {
                                val prev = templates[idx]
                                templates[idx] = prev.copy(
                                    selectedToolVersions = prev.selectedToolVersions + (tool to version),
                                )
                            }
                        } catch (t: Throwable) {
                            reporter.reportError("Update tool version failed", t)
                        }
                    }
                },
                onScaffoldTemplate = { templateId, destinationPath, projectName ->
                    // Resolve the display name from the current
                    // templates list so the progress modal shows
                    // "Kotlin + Gradle" rather than the UUID.
                    val templateName = templates.firstOrNull { it.id == templateId }?.name
                        ?: "template"

                    // Flip the detail pane closed before the job
                    // starts — users kicking off a scaffold expect
                    // to move on; keeping the pane open encourages
                    // them to hunt for feedback that's now in the
                    // progress modal. Progress state then takes
                    // over the "what's happening" story.
                    state.selectTemplate(null)
                    scaffoldProgress = ScaffoldProgress(templateName, projectName)

                    scaffoldJob = scope.launch {
                        try {
                            val result = templateRepo.scaffold(templateId, File(destinationPath), projectName)
                            if (result != null) {
                                // Show success toast + reveal the new
                                // directory so the user immediately
                                // sees what landed where.
                                reporter.report(
                                    "Scaffold created at ${result.destination}",
                                    ToastLevel.INFO,
                                )
                                revealInFileExplorer(File(result.destination))
                            } else {
                                reporter.report(
                                    "Scaffold failed: template or source missing",
                                    ToastLevel.ERROR,
                                )
                            }
                        } catch (_: CancellationException) {
                            // User hit Cancel in the progress modal.
                            // Any partial directory is left as-is;
                            // a follow-up scaffold attempt with an
                            // empty dir clears it via prepareEmpty.
                        } catch (t: Throwable) {
                            reporter.reportError("Scaffold failed", t)
                        } finally {
                            scaffoldProgress = null
                            scaffoldJob = null
                        }
                    }
                },
                onRefreshVersions = refreshTemplateVersions,
            )
            Tab.BACKUPS -> BackupsScreen(
                state = state,
                backups = backups,
                snapshots = snapshots,
                schedules = schedules,
                // Flows through to the Create Backup dialog's Target
                // field pre-fill. Blank state maps to "no default" —
                // BackupsScreen takes String? and treats blank/null
                // identically.
                defaultTargetDirectory = defaultBackupTargetDir.takeIf { it.isNotBlank() },
                onCreateBackup = { sourcePath, targetDir, includeHidden ->
                    // Manual creates are always foreground — show the
                    // progress dialog, auto-select, and honour the
                    // identical-backup confirmation flow.
                    maybeStartBackup(sourcePath, targetDir, /* silent = */ false, includeHidden)
                },
                onCreateSchedule = { spec ->
                    // Gate: refuse-to-silent-duplicate. Two schedules
                    // with the same (sourcePath, targetDirectory) pair
                    // usually means the user forgot they already set
                    // one up. Surface a confirmation dialog listing the
                    // existing one's cadence; the user can either
                    // cancel or intentionally add another (e.g. hourly
                    // incremental + weekly full on the same tree).
                    //
                    // Comparison is on the raw strings — no path
                    // normalisation. If a user adds one schedule with
                    // `/home/me/x` and another with `/home/me/x/`, they
                    // both match their typed form later; matching the
                    // user's literal input keeps the UX predictable.
                    val existing = schedules.firstOrNull {
                        it.sourcePath.pathEquals(spec.sourcePath) &&
                            PathEquality.equalNullable(it.targetDirectory, spec.targetDirectory)
                    }
                    if (existing != null) {
                        pendingDuplicateSchedule = PendingDuplicateSchedule(
                            spec = spec,
                            sourceName = java.io.File(spec.sourcePath).name
                                .ifBlank { spec.sourcePath },
                            existingCadence = existing.cadenceLabel(),
                        )
                    } else {
                        insertSchedule(
                            spec = spec,
                            schedulesStore = schedulesStore,
                            reloadSchedules = reloadSchedules,
                            reporter = reporter,
                        )
                    }
                },
                onDeleteBackup = { id ->
                    scope.launch {
                        try {
                            // Find the row up-front so we can also prune any
                            // linked snapshot from UI state once the repo
                            // tears the DB entries down.
                            val existing = backups.firstOrNull { it.id == id }
                            val ok = backupRepo.delete(id)
                            if (ok) {
                                backups.removeAll { it.id == id }
                                existing?.snapshotIds?.forEach { sid ->
                                    snapshots.removeAll { it.id == sid }
                                    rootNodes.remove(sid)
                                }
                                if (state.selectedBackupId == id) state.selectBackup(null)
                            }
                        } catch (t: Throwable) {
                            reporter.reportError("Delete backup failed", t)
                        }
                    }
                },
                onDeleteBackupGroup = { rootPath, alsoDeleteSchedules ->
                    // Bulk delete — iterates the group's NATIVE rows
                    // through the same single-delete pipeline so
                    // archive files, DB rows, and linked snapshot
                    // cleanup all happen exactly as they would per-
                    // row. Restic rows are deliberately skipped
                    // because the restic write path isn't wired; the
                    // dialog also filtered them out of its preview,
                    // so the user's confirmation was scoped to the
                    // same set we delete here.
                    //
                    // Snapshot of the target ids up-front so we
                    // iterate over a stable set even as `backups`
                    // shrinks between rounds.
                    val targetIds = backups
                        .filter { it.rootPath.pathEquals(rootPath) && it.backend == BackupBackend.NATIVE }
                        .map { it.id }
                    // Same snapshot for schedules — we capture ids
                    // before mutation so a concurrent scheduler tick
                    // that tried to upsert a schedule mid-delete
                    // couldn't sneak into the deletion set.
                    val scheduleIdsToDelete = if (alsoDeleteSchedules) {
                        schedules
                            .filter { it.sourcePath.pathEquals(rootPath) }
                            .map { it.id }
                    } else emptyList()

                    if (targetIds.isEmpty() && scheduleIdsToDelete.isEmpty()) {
                        return@BackupsScreen
                    }
                    scope.launch {
                        var deleted = 0
                        for (id in targetIds) {
                            try {
                                val existing = backups.firstOrNull { it.id == id }
                                val ok = backupRepo.delete(id)
                                if (ok) {
                                    backups.removeAll { it.id == id }
                                    existing?.snapshotIds?.forEach { sid ->
                                        snapshots.removeAll { it.id == sid }
                                        rootNodes.remove(sid)
                                    }
                                    if (state.selectedBackupId == id) state.selectBackup(null)
                                    deleted++
                                }
                            } catch (t: Throwable) {
                                // Per-row failures are logged but don't
                                // abort the whole bulk — better to clear
                                // what we can than leave the user with
                                // a half-deleted group and no recovery.
                                reporter.reportError("Delete backup failed for $id", t)
                            }
                        }

                        // Cascade-delete the attached schedules after
                        // the backups so a scheduler tick landing
                        // between backup-delete and schedule-delete
                        // can't resurrect one of the very backups we
                        // just removed. schedulesStore.delete() is
                        // per-id and idempotent — failures log and
                        // the loop continues.
                        var schedulesDeleted = 0
                        for (sid in scheduleIdsToDelete) {
                            try {
                                schedulesStore.delete(sid)
                                schedulesDeleted++
                            } catch (t: Throwable) {
                                reporter.reportError("Delete schedule failed for $sid", t)
                            }
                        }
                        if (scheduleIdsToDelete.isNotEmpty()) reloadSchedules()

                        // Close the group-history pane if we just
                        // emptied the group. Nothing to view anymore.
                        if (state.selectedBackupGroupPath == rootPath &&
                            backups.none { it.rootPath.pathEquals(rootPath) }) {
                            state.selectBackupGroup(null)
                        }
                        // Single toast summarising everything that
                        // landed — folding the schedule count in
                        // avoids a second toast stacking on top of
                        // the backup one.
                        val parts = buildList {
                            add("Deleted $deleted backup" + if (deleted == 1) "" else "s")
                            if (schedulesDeleted > 0) {
                                add("$schedulesDeleted schedule" + if (schedulesDeleted == 1) "" else "s")
                            }
                        }
                        reporter.report(
                            parts.joinToString(" and "),
                            org.open.file.ui.notifications.ToastLevel.INFO,
                        )
                    }
                },
                onRestoreBackup = { id, destPath ->
                    // Kick off the unzip on IO. We intentionally don't
                    // mutate any UI list here — restore produces files
                    // on disk but doesn't alter the backups / snapshots
                    // collections. On success we reveal the destination
                    // so the user immediately sees what was extracted.
                    scope.launch {
                        try {
                            val result = backupRepo.restore(id, destPath)
                            if (result != null) {
                                revealInFileExplorer(File(result.destination))
                                // Partial restores surface as a
                                // warning toast with the skipped
                                // count + the first couple of
                                // offending paths so the user can
                                // see WHAT got skipped without the
                                // toast turning into a stack
                                // trace. Full log remains in
                                // stderr/the error log for deeper
                                // triage.
                                if (result.skipped.isNotEmpty()) {
                                    val sampleEntries = result.skipped
                                        .take(3)
                                        .joinToString("; ") { "${it.path} (${it.reason})" }
                                    val more = if (result.skipped.size > 3) {
                                        " …and ${result.skipped.size - 3} more"
                                    } else ""
                                    reporter.report(
                                        "Restored ${result.fileCount} files, skipped " +
                                            "${result.skipped.size} (access issues): " +
                                            "$sampleEntries$more",
                                        ToastLevel.WARNING,
                                    )
                                } else {
                                    reporter.report(
                                        "Restored ${result.fileCount} files to ${result.destination}",
                                        ToastLevel.INFO,
                                    )
                                }
                            } else {
                                reporter.report(
                                    "Restore failed: backup not found or archive missing",
                                    ToastLevel.ERROR,
                                )
                            }
                        } catch (t: Throwable) {
                            reporter.reportError("Restore backup failed", t)
                        }
                    }
                },
                onOpenBackup = { id ->
                    // Reveal is synchronous-ish (fires a subprocess) and
                    // pulls the archive path off the UI model — no disk
                    // read needed, so we don't bother with the IO
                    // dispatcher here.
                    backups.firstOrNull { it.id == id }?.let { row ->
                        val revealed = revealInFileExplorer(File(row.destination))
                        if (!revealed) {
                            reporter.report(
                                "Could not open ${row.destination} in file explorer",
                                ToastLevel.WARNING,
                            )
                        }
                    }
                },
                onNavigateToSnapshot = { sid -> state.navigateToSnapshot(sid) },
                onOpenSchedules = { showSchedules = true },
            )
        }
            }
        }

        // Toast host is layered on top of the app content inside the
        // BoxWithConstraints so it spans the full window regardless of
        // which tab is active. Bottom-aligned per the user's request.
        ToastHost(toaster = toaster, modifier = Modifier.fillMaxSize())
    }

    // Settings (umbrella) dialog. Currently scoped to Theme controls;
    // future display / behaviour toggles will add more sections here
    // without changing the caller contract.
    SettingsDialog(
        visible = showSettings,
        selectedPalette = paletteName,
        selectedAccent = accentName,
        selectedFont = fontChoice,
        warnIdenticalBackups = warnIdenticalBackups,
        defaultBackupTargetDir = defaultBackupTargetDir,
        minimizeToTray = minimizeToTray,
        runOnStartup = autostartStatus.enabled,
        runOnStartupReason = if (autostartStatus.supported) null else autostartStatus.reason,
        resticRepoPath = resticRepoPath,
        resticPasswordFile = resticPasswordFile,
        onPaletteSelected = { palette ->
            palette.apply()
            paletteName = palette.name
            themePrefs.setPalette(palette.name)
            // Re-applying a palette resets its default accent — if the
            // user had picked a custom accent, re-layer it so the choice
            // sticks across palette changes.
            accentName?.let { name ->
                org.open.file.ui.theme.builtInAccents.firstOrNull { it.name == name }
                    ?.let { org.open.file.ui.theme.applyAccent(it.accent, it.light) }
            }
        },
        onAccentSelected = { accent ->
            org.open.file.ui.theme.applyAccent(accent.accent, accent.light)
            accentName = accent.name
            themePrefs.setAccent(accent.name)
        },
        onFontSelected = { choice ->
            // setFont persists AND writes to AppFonts.primary, which
            // the state-backed AppTypography getters read — so every
            // Text recomposes with the new family on the next frame.
            fontChoice = choice
            themePrefs.setFont(choice)
        },
        onWarnIdenticalToggled = { enabled ->
            // Apply immediately + persist. The state var is the source
            // of truth for maybeStartBackup; the prefs file keeps the
            // choice across restarts.
            warnIdenticalBackups = enabled
            backupPrefs.setWarnIdentical(enabled)
        },
        onDefaultBackupTargetDirChanged = { path ->
            // Keystroke-level persistence, consistent with the
            // Restic fields. Blank writes clear the pref (see
            // BackupPreferences.setDefaultTargetDirectory) so the
            // Create Backup dialog stops pre-filling.
            defaultBackupTargetDir = path
            backupPrefs.setDefaultTargetDirectory(path)
        },
        onMinimizeToTrayToggled = onMinimizeToTrayChanged,
        onRunOnStartupToggled = { enabled ->
            // Dispatch through AutostartManager. setEnabled() returns
            // the post-write Status — mirror it into our state var so
            // the UI reflects the actual OS outcome (not the requested
            // one, in case the registry write failed or the file got
            // stuck). The `supported` flag can flip to false if the
            // executable path went away between composition and
            // click — we surface the reason via the reporter.
            val result = org.open.file.ui.util.AutostartManager.setEnabled(enabled)
            autostartStatus = result
            if (!result.supported && result.reason != null) {
                reporter.reportError("Can't change startup setting: ${result.reason}", RuntimeException(result.reason))
            }
        },
        onResticConfigChanged = { repo, password ->
            // Persist per keystroke and rebuild the backups list so
            // the merged view updates as the user edits. listSnapshots
            // runs on IO inside reloadBackups, so this doesn't stall
            // the EDT on every character.
            resticRepoPath = repo
            resticPasswordFile = password
            resticPrefs.save(repo, password)
            scope.launch { reloadBackups() }
        },
        onDismiss = { showSettings = false },
    )

    // Recurring-backup schedules dialog. Kept here at App-level (next
    // to Settings / Language) so its state isn't tied to the Backups
    // screen's composition — opening it, switching tabs, and coming
    // back doesn't reset the add-form.
    ScheduleBackupDialog(
        visible = showSchedules,
        schedules = schedules,
        onDelete = { id ->
            schedulesStore.delete(id)
            reloadSchedules()
        },
        onToggleEnabled = { id, enabled ->
            val current = schedules.firstOrNull { it.id == id } ?: return@ScheduleBackupDialog
            schedulesStore.upsert(current.copy(enabled = enabled))
            reloadSchedules()
        },
        onDismiss = { showSchedules = false },
    )

    // Identical-backup confirmation. Rendered at the same App-level
    // stratum as the progress dialog because both sit above the
    // screen's own content. Confirming kicks off `startBackup` →
    // progress dialog → toast on success.
    pendingIdenticalBackup?.let { pending ->
        IdenticalBackupDialog(
            visible = true,
            existingName = pending.existingName,
            existingTimeAgo = pending.existingTimeAgo,
            onConfirm = {
                pendingIdenticalBackup = null
                // Confirm path is always foreground — the dialog only
                // fires for non-silent runs, so we don't need to
                // propagate a silent flag from pending state.
                startBackup(pending.sourcePath, pending.targetDirectory, /* silent = */ false, pending.includeHidden)
            },
            onDismiss = { pendingIdenticalBackup = null },
        )
    }

    // Duplicate-schedule confirmation. Same lifecycle as the
    // identical-backup dialog — stashed state triggers the modal,
    // confirm routes through the shared [insertSchedule] path, cancel
    // clears the stash. Rendered here at App-level so it layers above
    // the Backups screen regardless of which tab is active.
    pendingDuplicateSchedule?.let { pending ->
        DuplicateScheduleDialog(
            visible = true,
            sourceName = pending.sourceName,
            existingCadence = pending.existingCadence,
            onConfirm = {
                pendingDuplicateSchedule = null
                insertSchedule(
                    spec = pending.spec,
                    schedulesStore = schedulesStore,
                    reloadSchedules = reloadSchedules,
                    reporter = reporter,
                )
            },
            onDismiss = { pendingDuplicateSchedule = null },
        )
    }

    // Language picker — globe icon in the sidebar footer. Applying swaps
    // LocalStrings at the Window level (see main()) so every composable
    // re-renders with the new language on the next frame.
    LanguageDialog(
        visible = showLanguage,
        selectedCode = currentLocaleCode,
        onSelected = { locale ->
            onLocaleSelected(locale)
            showLanguage = false
        },
        onDismiss = { showLanguage = false },
    )

    // Interruptible progress dialog for backup creation. Visible whenever
    // `backupProgress` is non-null, which the Job's try/finally drives —
    // so both successful completion and user cancel dismiss it once the
    // Job unwinds.
    backupProgress?.let { p ->
        val s = LocalStrings.current
        val phaseLabel = when (p.phase) {
            BackupArchiver.Phase.SCANNING -> s.progressScanning
            BackupArchiver.Phase.COMPRESSING -> s.progressCompressingFormat.format(p.filesProcessed, p.totalFiles)
        }
        // Determinate only in the compressing phase; during scan we don't
        // know the total yet, so the bar is indeterminate.
        val pct = if (p.phase == BackupArchiver.Phase.COMPRESSING && p.totalFiles > 0) {
            p.filesProcessed.toFloat() / p.totalFiles.toFloat()
        } else null

        ProgressDialog(
            visible = true,
            title = s.progressCreatingBackup,
            phase = phaseLabel,
            detail = p.currentFile,
            progress = pct,
            onCancel = { backupJob?.cancel() },
        )
    }

    // Scaffold progress modal. Indeterminate — scaffold commands
    // don't report granular progress (gradle init / npm create
    // vite / etc. either block on the subprocess or stream stdout
    // we don't parse), so the bar is a "something is happening"
    // indicator rather than a percent complete. Cancel cooperatively
    // interrupts the coroutine; the ToolExecutor's subprocess gets
    // killed by the coroutine's cancellation propagating through
    // the runInterruptible boundary.
    scaffoldProgress?.let { sp ->
        ProgressDialog(
            visible = true,
            title = "Generating \"${sp.projectName}\"",
            phase = "Scaffolding ${sp.templateName}",
            detail = "Running the template's setup commands — this can take a minute for templates that download dependencies.",
            progress = null,
            onCancel = { scaffoldJob?.cancel() },
        )
    }
}

/**
 * Sanity-check a persisted window state against the *current* monitor
 * layout before handing it to Compose. Returns a new state with the
 * offending fields (size, position, or both) nulled out; nullables
 * downstream fall back to the built-in defaults.
 *
 * Three common failure modes we catch:
 *  1. Monitor unplugged — saved Absolute(x, y) now sits outside every
 *     connected screen. Restoring it would open the window at a
 *     location the user can't see, let alone click.
 *  2. Resolution shrank (docked → laptop). Saved 1920×1200 no longer
 *     fits a 1366×768 panel, so we fall back to the default 1200×780
 *     (which our min-size also honours).
 *  3. Headless / zero screens (remote session mid-boot, or an
 *     exception enumerating devices). We can't reason about geometry
 *     so we wipe both size and position and let Compose pick safe
 *     defaults.
 *
 * The overlap threshold (100×100 dp of the window inside *some*
 * monitor) is a heuristic — small enough that a user who parked the
 * window mostly off-screen doesn't get reset on them, large enough
 * that a saved position on a now-disconnected second monitor is
 * caught reliably.
 */
private fun validateWindowState(state: WindowPreferences.State): WindowPreferences.State {
    val bounds: List<java.awt.Rectangle> = try {
        val env = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
        if (env.isHeadlessInstance) emptyList()
        else env.screenDevices.map { it.defaultConfiguration.bounds }
    } catch (_: Throwable) {
        // Any AWT failure (headless, display init race, driver crash)
        // → treat as "no screens known" so we drop geometry and fall
        // to platform defaults rather than bombing out at startup.
        emptyList()
    }

    if (bounds.isEmpty()) {
        return state.copy(width = null, height = null, x = null, y = null)
    }

    val maxW = bounds.maxOf { it.width }
    val maxH = bounds.maxOf { it.height }

    // If the saved size wouldn't fit any connected monitor (with a
    // little slack for window chrome / taskbar), drop it. 95% lets
    // a maximised-then-restored window on the same monitor survive.
    val sizeFits = (state.width == null || state.width.value <= maxW * 0.95f) &&
        (state.height == null || state.height.value <= maxH * 0.95f)
    // Also enforce the floor — a prefs file from an older build that
    // let the user shrink below our new minimum would otherwise
    // re-open the app at an unusable size. `coerceAtLeast` keeps the
    // saved value when it's already big enough.
    val safeWidth = if (sizeFits) state.width?.let {
        it.value.coerceAtLeast(MIN_WINDOW_WIDTH_DP.toFloat()).dp
    } else null
    val safeHeight = if (sizeFits) state.height?.let {
        it.value.coerceAtLeast(MIN_WINDOW_HEIGHT_DP.toFloat()).dp
    } else null

    // If the saved position doesn't overlap *any* monitor by at least
    // a tile's worth (≥100×100 dp), treat it as off-screen and drop.
    // Using the validated size here — if we just nulled out the
    // size, fall back to the default 1200×780 for the overlap check.
    val (safeX, safeY) = run {
        val x = state.x
        val y = state.y
        if (x == null || y == null) return@run null to null
        val w = (safeWidth ?: 1200.dp).value.toInt()
        val h = (safeHeight ?: 780.dp).value.toInt()
        val winRect = java.awt.Rectangle(x.value.toInt(), y.value.toInt(), w, h)
        val minOverlap = 100
        val visible = bounds.any { monitor ->
            val inter = winRect.intersection(monitor)
            !inter.isEmpty && inter.width >= minOverlap && inter.height >= minOverlap
        }
        if (visible) x to y else null to null
    }

    return state.copy(
        width = safeWidth,
        height = safeHeight,
        x = safeX,
        y = safeY,
    )
}
