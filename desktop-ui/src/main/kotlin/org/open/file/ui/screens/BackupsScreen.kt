package org.open.file.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.open.file.snapshot.models.SavedSnapshot
import org.open.file.ui.components.*
import org.open.file.ui.data.BackupSchedule
import org.open.file.ui.i18n.LocalStrings
import org.open.file.ui.state.AppState
import org.open.file.ui.theme.*
import org.open.file.ui.util.PathEquality
import org.open.file.ui.util.PathValidation
import org.open.file.ui.util.handOnHover
import org.open.file.ui.util.pathEquals
import org.open.file.ui.util.pickDirectory
import org.open.file.ui.util.validateDirectory
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant

// ──────────────────────────────────────────────
// Backup UI model
// ──────────────────────────────────────────────

data class BackupUiModel(
    val id: String,
    val name: String,
    /**
     * Absolute path of the *source* directory the archive was made from.
     * Used (beyond just storing it) to group backups sharing the same
     * target into a history timeline in the detail pane. Distinct from
     * [destination], which is the path to the archive file itself.
     */
    val rootPath: String,
    val snapshotIds: List<String>,
    val destination: String,
    /**
     * Parent directory the archive was written to. Same as
     * `File(destination).parent` by default, but stored explicitly so
     * the detail pane can surface the user's chosen destination folder
     * (the zip filename is noise) and "new backup from same source" /
     * scheduled reruns reuse the same target without re-deriving it.
     */
    val targetDirectory: String? = null,
    val createdAt: Instant,
    val size: String,
    val status: BackupStatus,
    val compression: String,
    /**
     * Raw pre-compression byte count for this backup — kept alongside
     * the formatted [size] so the "identical backup" pre-check can do
     * exact comparisons without re-parsing the human-readable string.
     */
    val originalSizeBytes: Long = 0L,
    /**
     * Total file + directory entries in the archived tree. Used
     * together with [originalSizeBytes] as a cheap duplicate-content
     * fingerprint (no hashing, just stat calls).
     */
    val entryCount: Int = 0,
    /**
     * Which subsystem owns this row. Drives the "restic" chip + gates
     * mutation actions that aren't wired yet for external backends.
     */
    val backend: BackupBackend = BackupBackend.NATIVE,
    /**
     * Whether this archive was created with hidden files included.
     * Read by the replay path ("new backup from same source") so the
     * rerun uses the same toggle the user picked originally rather
     * than silently defaulting to true.
     */
    val includeHidden: Boolean = true,
)

enum class BackupStatus(val label: String, val color: Color) {
    COMPLETED("completed", AppColors.success),
    RUNNING("running", AppColors.warning),
    FAILED("failed", AppColors.error),
}

/**
 * Which backend manages this row. Native = our SQLite + ZIP pipeline
 * (`BackupArchiver`). Restic = an external restic repository, surfaced
 * read-only via the `ResticBackend` adapter — delete / restore
 * actions are hidden for those rows until the write paths land.
 */
enum class BackupBackend { NATIVE, RESTIC }

/**
 * Discriminated union the Create-Backup dialog hands to Main.kt when
 * the user picks "Run on a schedule". Keeps the UI layer agnostic of
 * which [BackupSchedule] factory to call — Main.kt pattern-matches and
 * routes to [BackupSchedule.interval] or [BackupSchedule.cron].
 */
sealed interface ScheduleSpec {
    val sourcePath: String
    val targetDirectory: String?
    /** Mirror of BackupArchiver's includeHidden — threaded into BackupSchedule. */
    val includeHidden: Boolean

    data class Interval(
        override val sourcePath: String,
        override val targetDirectory: String?,
        override val includeHidden: Boolean,
        val intervalMinutes: Long,
    ) : ScheduleSpec

    data class Cron(
        override val sourcePath: String,
        override val targetDirectory: String?,
        override val includeHidden: Boolean,
        val expression: String,
    ) : ScheduleSpec
}

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a").withZone(ZoneId.systemDefault())

fun timeAgo(instant: Instant): String {
    val diff = Clock.System.now().toEpochMilliseconds() - instant.toEpochMilliseconds()
    val mins = diff / 60_000
    return when {
        mins < 60 -> "${mins}m ago"
        mins < 1440 -> "${mins / 60}h ago"
        mins < 43200 -> "${mins / 1440}d ago"
        else -> dateFormatter.format(instant.toJavaInstant())
    }
}

// ──────────────────────────────────────────────
// Backups Screen
// ──────────────────────────────────────────────

@Composable
fun BackupsScreen(
    state: AppState,
    backups: List<BackupUiModel>,
    snapshots: List<SavedSnapshot>,
    /**
     * Kick off a backup. [targetDirectory] is null when the user wants
     * the default (~/.open-file/backups) — callers should treat a null
     * value as "no preference" and let the repo choose.
     * [includeHidden] false skips hidden files / dotdirs from the tree
     * walk (avoids bloating archives with `.git/`, `.cache/`, etc.).
     */
    onCreateBackup: (sourcePath: String, targetDirectory: String?, includeHidden: Boolean) -> Unit,
    /**
     * Persist a recurring schedule. [spec] carries either an interval
     * or a cron expression; Main.kt decides which factory to call on
     * [BackupSchedule]. Separate callback from [onCreateBackup] so the
     * immediate-backup path stays a single responsibility.
     */
    onCreateSchedule: (spec: ScheduleSpec) -> Unit,
    onDeleteBackup: (backupId: String) -> Unit,
    /**
     * Bulk-delete every backup sharing [rootPath] — fires only after
     * the user has cleared the type-to-confirm gate. Main.kt iterates
     * matching rows through the normal delete pipeline so each row's
     * archive file + DB row cleanup runs identically to single deletes.
     *
     * [alsoDeleteSchedules] is the dialog's checkbox state: when true,
     * any [BackupSchedule] whose `sourcePath` matches [rootPath] is
     * also removed. Lets users clean up "5 mistaken backups" + the
     * runaway schedule feeding them in one confirmation.
     */
    onDeleteBackupGroup: (rootPath: String, alsoDeleteSchedules: Boolean) -> Unit,
    /**
     * Extract the chosen backup's archive into [destinationPath]. Called
     * after the user picks a destination; the actual unzip happens on an
     * IO dispatcher inside the repository.
     */
    onRestoreBackup: (backupId: String, destinationPath: String) -> Unit,
    /**
     * Reveal the archive on disk in the host's native file manager.
     * Synchronous — the launch completes quickly and the handler logs
     * rather than throwing if the host has no file manager.
     */
    onOpenBackup: (backupId: String) -> Unit,
    /**
     * Jump to the Snapshots tab and select the given snapshot id. Fired
     * when the user clicks a snapshot row inside a backup's detail
     * panel — a cross-tab navigation that would otherwise require
     * switching manually and finding the row again.
     */
    onNavigateToSnapshot: (snapshotId: String) -> Unit,
    /**
     * Open the recurring-backup schedules modal. Provided as a callback
     * so the dialog + scheduler state live at the Main.kt level.
     */
    onOpenSchedules: () -> Unit,
    /**
     * Current schedules snapshot — passed in so the bulk-delete
     * dialog can show the user how many schedules are attached to
     * the group's source directory and include them in the preview.
     * Main.kt owns the state list; we just need a read view here.
     */
    schedules: List<org.open.file.ui.data.BackupSchedule>,
    /**
     * Persisted default target directory from [BackupPreferences].
     * When non-null, the Create Backup dialog pre-fills its Target
     * field with this path. Null (or blank) means no preference —
     * the dialog shows an empty field and falls back to the
     * PathHints placeholder.
     */
    defaultTargetDirectory: String? = null,
) {
    // Recomputed on every composition. The old `remember(backups, …)`
    // wrapper around this and `groups` was a Compose pitfall: `backups`
    // is a SnapshotStateList whose *reference* is stable across add /
    // remove mutations, so remember kept returning a stale snapshot
    // until something else invalidated the composition (e.g. swapping
    // tabs). Reading `backups` directly here subscribes the composition
    // to snapshot-list changes, so creates and deletes now flow through
    // to both the list view and the detail pane.
    // distinctBy { id } before the filter so a duplicate row (e.g.
    // the scheduler's completion path racing with a focus-regain
    // reloadBackups, or a restic+native id collision we'd rather
    // swallow than crash on) can't reach LazyColumn — which throws
    // a loud "Key X is already used" the moment it spots the
    // duplicate. Cheap on the list sizes we see.
    val filtered: List<BackupUiModel> = backups.distinctBy { it.id }.let { deduped ->
        if (state.filterText.isBlank()) {
            deduped
        } else {
            val q = state.filterText.lowercase()
            deduped.filter {
                it.name.lowercase().contains(q) ||
                    it.destination.lowercase().contains(q) ||
                    it.status.label.contains(q)
            }
        }
    }

    val selected = filtered.find { it.id == state.selectedBackupId }
    // Group selection — set when the user clicks a multi-backup grouped
    // parent row. Drives the right-hand pane's "group history only" view.
    val selectedGroupPath = state.selectedBackupGroupPath
    var showCreate by remember { mutableStateOf(false) }

    // Group by source path. Groups with one backup render as a plain
    // row (same as every other tab); multi-backup groups render as an
    // expandable parent + indented children. Sorted so groups with the
    // most recent backup float to the top. Computed inline (not
    // wrapped in `remember`) for the same SnapshotStateList-subscription
    // reason above — grouping is cheap on typical N.
    // Group by the *normalized* rootPath — on Windows two rows could
    // legitimately have `C:\Users\Mike` and `c:\users\mike\` and
    // they're the same directory on disk, so a case-sensitive groupBy
    // would split them into distinct groups and the UI would look
    // doubled-up. PathEquality.normalize() handles the case + trailing
    // slash quirks without touching the original display strings.
    val groups = filtered
        .groupBy { PathEquality.normalize(it.rootPath) }
        .toList()
        // Use the most recent backup's original rootPath as the
        // group's display key so the shown casing matches what the
        // user last typed.
        .map { (_, bs) ->
            val sorted = bs.sortedByDescending { it.createdAt }
            sorted.first().rootPath to sorted
        }
        .sortedByDescending { (_, bs) -> bs.first().createdAt }
    // Per-rootPath expand state, persisted for the duration of this
    // screen's composition so opening a group, clicking elsewhere, and
    // coming back keeps its prior state.
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    val s = LocalStrings.current
    Column(Modifier.fillMaxSize()) {
        HeaderBar(
            title = s.backupsTitle,
            subtitle = s.backupsSubtitleFormat.format(
                backups.size,
                backups.count { it.status == BackupStatus.COMPLETED },
            ),
            filterText = state.filterText,
            onFilterChange = { state.filterText = it },
            filterPlaceholder = s.backupsSearchPlaceholder,
            onCreateClick = { showCreate = true },
            createLabel = s.actionNew,
            // Alarm clock opens the recurring-backups modal — sits to
            // the left of the primary Create button so it's one click
            // away without crowding the header.
            onSecondaryClick = onOpenSchedules,
            secondaryIcon = Icons.Default.Schedule,
            secondaryLabel = "Schedules",
        )

        Row(Modifier.fillMaxSize()) {
            if (filtered.isEmpty()) {
                EmptyState(s.backupsEmpty)
            } else {
                LazyColumnWithScrollbar(modifier = Modifier.weight(1f)) {
                    groups.forEach { (rootPath, group) ->
                        if (group.size == 1) {
                            // Solo backup — render exactly like before,
                            // no grouping affordance at all.
                            val bk = group[0]
                            item(key = "single-${bk.id}") {
                                StandardBackupRow(
                                    bk = bk,
                                    selected = state.selectedBackupId == bk.id,
                                    onClick = { state.selectBackup(bk.id) },
                                    onDelete = { onDeleteBackup(bk.id) },
                                    s = s,
                                )
                            }
                        } else {
                            val isExpanded = expanded[rootPath] == true
                            item(key = "group-$rootPath") {
                                GroupHeaderRow(
                                    rootPath = rootPath,
                                    group = group,
                                    attachedSchedules = schedules.filter { it.sourcePath.pathEquals(rootPath) },
                                    expanded = isExpanded,
                                    selected = selectedGroupPath == rootPath,
                                    onClick = {
                                        // Selecting the group also auto-
                                        // opens it — clicking a collapsed
                                        // row without seeing its children
                                        // was a hit against discoverability.
                                        // The chevron still toggles
                                        // independently, so users can
                                        // collapse a selected group
                                        // without deselecting it.
                                        expanded[rootPath] = true
                                        state.selectBackupGroup(rootPath)
                                    },
                                    onToggleExpand = { expanded[rootPath] = !isExpanded },
                                    onDeleteGroup = { alsoDeleteSchedules ->
                                        onDeleteBackupGroup(rootPath, alsoDeleteSchedules)
                                    },
                                )
                            }
                            if (isExpanded) {
                                items(group, key = { "child-${it.id}" }) { bk ->
                                    ChildBackupRow(
                                        bk = bk,
                                        selected = state.selectedBackupId == bk.id,
                                        onClick = { state.selectBackup(bk.id) },
                                        onDelete = { onDeleteBackup(bk.id) },
                                        s = s,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Detail pane — dispatches between single-backup view and
            // group-history view based on which selection state is set.
            // Both are mutually exclusive thanks to AppState's selectors.
            if (selected != null) {
                BackupDetailPanel(
                    selected,
                    snapshots,
                    // Pass the full list so the panel can build its own
                    // same-rootPath history without a second repository
                    // round-trip — the list is already in memory for
                    // the list view anyway.
                    allBackups = backups,
                    onClose = { state.selectBackup(null) },
                    onDelete = { onDeleteBackup(selected.id) },
                    onRestore = { dest -> onRestoreBackup(selected.id, dest) },
                    onOpenInFileExplorer = { onOpenBackup(selected.id) },
                    onNavigateToSnapshot = onNavigateToSnapshot,
                    onSelectBackup = { bid -> state.selectBackup(bid) },
                    // Re-run a backup against this row's source dir.
                    // Uses the already-plumbed onCreateBackup path so
                    // the progress dialog + auto-select of the new row
                    // kick in exactly like the Create dialog flow.
                    // Also carries forward the selected backup's target
                    // directory so a replay lands next to the previous
                    // archive rather than back in the default folder.
                    // Replay uses the original backup's include-hidden
                    // choice — stored on the row since the column was
                    // added. Rows persisted before that land with the
                    // default `true`, matching pre-toggle behaviour.
                    onNewFromSameSource = {
                        onCreateBackup(selected.rootPath, selected.targetDirectory, selected.includeHidden)
                    },
                )
            } else if (selectedGroupPath != null) {
                BackupGroupHistoryPanel(
                    rootPath = selectedGroupPath,
                    allBackups = backups,
                    attachedSchedules = schedules.filter { it.sourcePath == selectedGroupPath },
                    onClose = { state.selectBackupGroup(null) },
                    onSelectBackup = { bid -> state.selectBackup(bid) },
                    // Reuse the most-recent group member's target so
                    // scheduled/group replays stay consistent — fall
                    // back to null if the group only has pre-column rows.
                    onNewFromSameSource = {
                        // Reuse the most-recent group member's target
                        // AND its includeHidden choice. Users who
                        // toggled exclusion once for a given directory
                        // likely want the same for replays of the
                        // same group. Defaults to true when the group
                        // has no recorded latest (shouldn't happen —
                        // we only render the group history panel when
                        // the group has members).
                        val latest = backups
                            .filter { it.rootPath.pathEquals(selectedGroupPath) }
                            .maxByOrNull { it.createdAt }
                        onCreateBackup(
                            selectedGroupPath,
                            latest?.targetDirectory,
                            latest?.includeHidden ?: true,
                        )
                    },
                    onDeleteGroup = { alsoDeleteSchedules ->
                        onDeleteBackupGroup(selectedGroupPath, alsoDeleteSchedules)
                    },
                )
            }
        }
    }

    CreateBackupDialog(
        visible = showCreate,
        defaultTargetDirectory = defaultTargetDirectory,
        onDismiss = { showCreate = false },
        onCreate = { sourcePath, targetDir, includeHidden ->
            onCreateBackup(sourcePath, targetDir, includeHidden)
            showCreate = false
        },
        onCreateSchedule = { spec ->
            onCreateSchedule(spec)
            showCreate = false
        },
    )
}

// ──────────────────────────────────────────────
// List rows — solo, group header, and grouped child
// ──────────────────────────────────────────────

/**
 * Plain backup row — used for rootPaths that only have a single backup.
 * Identical to the pre-grouping layout; extracted so the grouped branch
 * doesn't have to copy it.
 */
@Composable
private fun StandardBackupRow(
    bk: BackupUiModel,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    s: org.open.file.ui.i18n.Strings,
) {
    val rowStatus = when (bk.status) {
        BackupStatus.COMPLETED -> s.backupsStatusCompleted
        BackupStatus.RUNNING -> s.backupsStatusRunning
        BackupStatus.FAILED -> s.backupsStatusFailed
    }
    // Compact windows drop secondary text labels in the row —
    // with the name + two chips + dot + status text competing for
    // horizontal space, the status word used to wrap onto a second
    // line once the cell narrowed below its intrinsic content width.
    // At compact, dot alone communicates state; at medium/expanded
    // we show the label but force single-line (softWrap=false + clip).
    val metrics = org.open.file.ui.util.LocalLayoutMetrics.current
    val isCompact = metrics.sizeClass == org.open.file.ui.util.WindowSizeClass.COMPACT
    ListRow(
        selected = selected,
        onClick = onClick,
        icon = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.03f)),
            ) {
                Icon(Icons.Default.Archive, contentDescription = null, tint = AppColors.textMuted, modifier = Modifier.size(18.dp))
            }
        },
        content = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    bk.name,
                    style = AppTypography.rowTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Badge for restic-sourced rows so users can tell
                // them apart from native ZIPs at a glance. Hidden on
                // compact to free up horizontal space — the backend
                // is surfaced again in the detail pane, so no info
                // is lost on narrow windows.
                if (bk.backend == BackupBackend.RESTIC && !isCompact) {
                    SingleLineText(
                        "restic",
                        style = AppTypography.chip.copy(color = AppColors.accentLight),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AppColors.accentBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                StatusDot(bk.status.color)
                if (!isCompact) {
                    Text(
                        rowStatus,
                        style = AppTypography.chip.copy(color = bk.status.color),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                bk.destination,
                style = AppTypography.code.copy(color = AppColors.accent.copy(alpha = 0.6f)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailing = {
            // Trailing metadata — each label is single-line guarded so
            // a narrow detail pane can't character-stack "N snaps"
            // into a vertical column of letters.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleLineText("${bk.snapshotIds.size} snaps", style = AppTypography.codeSmall)
                SingleLineText(bk.size, style = AppTypography.codeSmall)
                SingleLineText(timeAgo(bk.createdAt), style = AppTypography.bodySmall)
            }
        },
        actions = {
            // Inline delete — skipped for restic-backed rows since
            // we don't own the write path for that backend yet.
            if (bk.backend == BackupBackend.NATIVE) {
                RowDeleteIcon(
                    itemLabel = s.itemBackup,
                    itemName = bk.name,
                    onConfirm = onDelete,
                )
            }
        },
    )
}

/**
 * Grouped parent row — summarises every backup sharing a rootPath into a
 * single expandable entry.
 *
 *  - Left icon is a directory glyph to distinguish from solo archive rows.
 *  - Trailing chevron rotates with [expanded] state as a quick visual cue.
 *  - Clicking the body selects the *group*, which makes the detail pane
 *    render the group-only history view. Clicking the chevron toggles
 *    expansion; [onClick] and [onToggleExpand] are wired to separate
 *    gestures so selection and expansion stay independent.
 */
@Composable
private fun GroupHeaderRow(
    rootPath: String,
    group: List<BackupUiModel>,
    /** Schedules for the same rootPath — rendered in the bulk-delete confirm dialog. */
    attachedSchedules: List<org.open.file.ui.data.BackupSchedule>,
    expanded: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleExpand: () -> Unit,
    onDeleteGroup: (alsoDeleteSchedules: Boolean) -> Unit,
) {
    val s = LocalStrings.current
    val latest = group.first()
    val displayName = java.io.File(rootPath).name.ifBlank { rootPath }

    // Type-to-confirm bulk delete. Opens when the row's trash icon is
    // clicked; dialog state lives at the row level so every group
    // manages its own without a screen-wide pending-state variable.
    var showBulkConfirm by remember(rootPath) { mutableStateOf(false) }
    // Default-on when schedules exist — the common case is "5
    // mistaken backups from a runaway schedule", and forgetting to
    // uncheck is less bad than forgetting to check (you'd be back
    // here tomorrow repeating the cleanup).
    var alsoDeleteSchedules by remember(rootPath, attachedSchedules.size) {
        mutableStateOf(attachedSchedules.isNotEmpty())
    }
    val nativeGroup = group.filter { it.backend == BackupBackend.NATIVE }
    ListRow(
        selected = selected,
        onClick = onClick,
        icon = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.accent.copy(alpha = 0.10f)),
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = AppColors.accentLight,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        content = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    displayName,
                    style = AppTypography.rowTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Translated "N backups" count chip. SingleLineText
                // keeps the chip on one line no matter how narrow the
                // content cell gets — without it, resizing the window
                // while the info pane is open used to make "5 backups"
                // character-stack vertically.
                SingleLineText(
                    s.backupsGroupCountFormat.format(group.size),
                    style = AppTypography.chip.copy(color = AppColors.accentLight),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppColors.accentBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                rootPath,
                style = AppTypography.code.copy(color = AppColors.accent.copy(alpha = 0.6f)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailing = {
            // Latest-activity timestamp + an explicit expand/collapse
            // button. Separating it from the row's onClick means the user
            // can expand without navigating, or navigate without changing
            // expansion state.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SingleLineText(timeAgo(latest.createdAt), style = AppTypography.bodySmall)
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(28.dp).handOnHover(),
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = AppColors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        // Bulk-delete trash — only offered when there's at least one
        // native row in the group (restic write paths aren't wired,
        // so we can't promise "delete everything for this directory"
        // on mixed groups). Tooltip spells out the bulk-action so
        // users don't confuse it with per-row delete.
        actions = if (nativeGroup.isNotEmpty()) {
            {
                AppTooltip(text = s.backupsBulkDeleteTooltipFormat.format(nativeGroup.size, displayName)) {
                    IconButton(
                        onClick = { showBulkConfirm = true },
                        modifier = Modifier.size(28.dp).handOnHover(),
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = s.backupsBulkDeleteContentDescriptionFormat.format(displayName),
                            tint = AppColors.textMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        } else null,
    )

    // Type-to-confirm gate for the bulk delete. Preview list shows
    // each backup's timestamp so the user can visually verify the
    // cohort before committing. Directory name is the expected type-
    // in token — users rarely have multiple directories with the
    // same last-segment name, so requiring the bare name keeps the
    // prompt short while still being a real guardrail.
    TypeToConfirmDialog(
        visible = showBulkConfirm,
        title = s.backupsBulkDeleteDialogTitle,
        headlineMessage = s.backupsBulkDeleteHeadlineFormat.format(nativeGroup.size, displayName),
        confirmName = displayName,
        itemPreview = nativeGroup.map { dateFormatter.format(it.createdAt.toJavaInstant()) },
        extraContent = if (attachedSchedules.isNotEmpty()) {
            {
                DeleteAttachedSchedulesCheckbox(
                    schedules = attachedSchedules,
                    checked = alsoDeleteSchedules,
                    onCheckedChange = { alsoDeleteSchedules = it },
                )
            }
        } else null,
        onConfirm = {
            showBulkConfirm = false
            onDeleteGroup(alsoDeleteSchedules && attachedSchedules.isNotEmpty())
        },
        onDismiss = { showBulkConfirm = false },
    )
}

/**
 * Indented child row under a group header. Same archive icon as the
 * solo case but visually offset with a left margin so the hierarchy
 * reads at a glance.
 */
@Composable
private fun ChildBackupRow(
    bk: BackupUiModel,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    s: org.open.file.ui.i18n.Strings,
) {
    val rowStatus = when (bk.status) {
        BackupStatus.COMPLETED -> s.backupsStatusCompleted
        BackupStatus.RUNNING -> s.backupsStatusRunning
        BackupStatus.FAILED -> s.backupsStatusFailed
    }
    // Same compact-mode gate as StandardBackupRow — child rows are
    // even tighter because the left-indent eats another 24dp, so
    // they're the first to overflow. Dot-only at compact; labelled
    // + single-line everywhere else.
    val metrics = org.open.file.ui.util.LocalLayoutMetrics.current
    val isCompact = metrics.sizeClass == org.open.file.ui.util.WindowSizeClass.COMPACT
    // Wrap the ListRow in a left-padded Box to produce the indent
    // without having to branch the ListRow internals.
    Box(modifier = Modifier.padding(start = 24.dp)) {
        ListRow(
            selected = selected,
            onClick = onClick,
            icon = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.03f)),
                ) {
                    Icon(Icons.Default.Archive, contentDescription = null, tint = AppColors.textMuted, modifier = Modifier.size(14.dp))
                }
            },
            content = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Timestamp serves as the child row's "title" — inside
                    // a group the distinguishing attribute isn't the
                    // directory name (they all share it) but *when*.
                    Text(
                        dateFormatter.format(bk.createdAt.toJavaInstant()),
                        style = AppTypography.body.copy(fontSize = 12.5.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    StatusDot(bk.status.color)
                    if (!isCompact) {
                        Text(
                            rowStatus,
                            style = AppTypography.chip.copy(color = bk.status.color),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            },
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SingleLineText(bk.size, style = AppTypography.codeSmall)
                    SingleLineText(timeAgo(bk.createdAt), style = AppTypography.bodySmall)
                }
            },
            actions = {
                // Same gate as the parent row — restic rows hide the
                // trash until we have a working restic delete path.
                if (bk.backend == BackupBackend.NATIVE) {
                    RowDeleteIcon(
                        itemLabel = s.itemBackup,
                        // Use the timestamp for the name in children
                        // so the confirmation dialog doesn't repeat
                        // the parent directory's name for every row.
                        itemName = dateFormatter.format(bk.createdAt.toJavaInstant()),
                        onConfirm = onDelete,
                    )
                }
            },
        )
    }
}

// ──────────────────────────────────────────────
// Backup Detail Panel
// ──────────────────────────────────────────────

@Composable
private fun BackupDetailPanel(
    backup: BackupUiModel,
    allSnapshots: List<SavedSnapshot>,
    /**
     * Every backup in the store, so the panel can group by source path
     * and render the chronological history timeline in-place.
     */
    allBackups: List<BackupUiModel>,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onRestore: (destinationPath: String) -> Unit,
    onOpenInFileExplorer: () -> Unit,
    onNavigateToSnapshot: (snapshotId: String) -> Unit,
    /**
     * Called when the user clicks another row in the history list —
     * swaps which backup the detail pane is showing without leaving
     * the Backups tab.
     */
    onSelectBackup: (backupId: String) -> Unit,
    /**
     * Fire off a new backup with the same rootPath as [backup] — one-
     * click shortcut for "I want another snapshot of this directory
     * right now" without re-entering the Create dialog.
     */
    onNewFromSameSource: () -> Unit,
) {
    val linkedSnapshots = allSnapshots.filter { it.id in backup.snapshotIds }
    // History timeline — every backup sharing this one's source directory,
    // newest first. Recomputed only when the id or list composition
    // actually changes so ListRow selections don't churn the timeline.
    val history = remember(backup.rootPath, allBackups) {
        allBackups
            .filter { it.rootPath.pathEquals(backup.rootPath) }
            .sortedByDescending { it.createdAt }
    }
    // 1-based position for display; chronological (newest = 1). Falls
    // back to 0 only if the list doesn't contain the current backup,
    // which shouldn't happen in practice but keeps the UI honest.
    val positionNewestFirst = history.indexOfFirst { it.id == backup.id }.let { if (it < 0) 0 else it + 1 }
    // Confirm-before-delete gate — mirrors snapshot / template detail panels.
    var confirmDelete by remember(backup.id) { mutableStateOf(false) }
    // Restore flow goes through an in-app modal (RestoreBackupDialog)
    // instead of jumping straight to the OS picker, so the user can
    // review + edit the destination path (default-filled from the
    // backup's original source).
    var showRestore by remember(backup.id) { mutableStateOf(false) }

    DetailPanel(
        onClose = onClose,
        header = {
            Text(backup.name, style = AppTypography.pageTitle.copy(fontSize = 15.sp))
        }
    ) {
        val sDetail = LocalStrings.current
        val statusLabel = when (backup.status) {
            BackupStatus.COMPLETED -> sDetail.backupsStatusCompleted
            BackupStatus.RUNNING -> sDetail.backupsStatusRunning
            BackupStatus.FAILED -> sDetail.backupsStatusFailed
        }

        // Actions at the top of the body. Restic-backed rows are
        // browse-only for now — the create/restore/delete shell-outs
        // haven't been wired yet, so we hide those buttons rather
        // than offering no-ops.
        //
        // Forward-actions (New From Same Source + Restore) share the
        // first row; Delete lives on its own row below. Three buttons
        // on one row was visibly squished in the 360dp-wide detail
        // pane — "Delete" lost its label almost entirely. Splitting
        // gives Delete the horizontal room to render its icon + full
        // label at any pane width, and keeps the destructive verb
        // visually separated from the forward ones as a bonus.
        val isNative = backup.backend == BackupBackend.NATIVE
        if (isNative) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    sDetail.backupsNewFromSameSource,
                    Icons.Default.Replay,
                    onClick = onNewFromSameSource,
                    backgroundColor = AppColors.accentBg,
                    borderColor = AppColors.accentBorder,
                    color = AppColors.accentLight,
                )
                ActionButton(
                    sDetail.actionRestore, Icons.Default.Restore,
                    // Opens the in-app RestoreBackupDialog rather than
                    // the OS picker directly — the modal prefills the
                    // destination with the backup's original source
                    // so "restore over the original" is one click.
                    onClick = { showRestore = true },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DangerButton(sDetail.actionDelete, Icons.Default.Delete, onClick = { confirmDelete = true })
            }
        }

        // Status
        DetailField(sDetail.backupsStatusLabel) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusDot(backup.status.color, modifier = Modifier.size(8.dp))
                Text(
                    statusLabel,
                    style = AppTypography.body.copy(color = backup.status.color)
                )
            }
        }

        // Destination + inline reveal — the "Open in File Explorer" affordance
        // lives here instead of in the action row at the bottom so the user's
        // eye connects the button to the path it acts on.
        DetailField(sDetail.backupsDestinationLabel) {
            // Clickable-to-copy chip — mirrors DetailFieldCode's
            // behaviour but inlined because we need the dedicated
            // "Open in file explorer" icon button alongside. The chip
            // and the icon are separate click targets, so copying and
            // revealing don't race.
            var destCopied by remember(backup.destination) { mutableStateOf(false) }
            LaunchedEffect(destCopied) {
                if (destCopied) {
                    kotlinx.coroutines.delay(1200)
                    destCopied = false
                }
            }
            // Column so the confirmation row stacks below the chip +
            // reveal button rather than trying to squeeze in between
            // (which on long paths shrank to a vertical sliver).
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        backup.destination,
                        style = AppTypography.code,
                        // Let long paths wrap onto as many lines as
                        // they need. Ellipsising the destination
                        // (the old `maxLines = 1`) hid the most
                        // useful information in the pane — the full
                        // archive path — behind a truncation that
                        // required a hover-tooltip or copy-paste to
                        // recover. With softWrap = true (default)
                        // Compose breaks unbreakable strings at
                        // character boundaries when needed, which
                        // is exactly what paths want.
                        softWrap = true,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(AppColors.accent.copy(alpha = 0.08f))
                            .clickable {
                                if (org.open.file.ui.util.copyToClipboard(backup.destination)) {
                                    destCopied = true
                                }
                            }
                            .handOnHover()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    IconButton(
                        onClick = onOpenInFileExplorer,
                        modifier = Modifier.size(28.dp).handOnHover(),
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = "Open in file explorer",
                            tint = AppColors.accentLight,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (destCopied) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = AppColors.success,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            LocalStrings.current.actionCopied,
                            style = AppTypography.bodySmall.copy(color = AppColors.success),
                        )
                    }
                }
            }
        }
        DetailFieldText(sDetail.backupsCreatedLabel, dateFormatter.format(backup.createdAt.toJavaInstant()))

        // Explicit target-directory line — handy when the destination
        // filename is long and the user just wants to see the folder
        // they chose. Only render when distinct from the archive path
        // (i.e. the row has a recorded target) so we don't duplicate
        // info for older pre-column backups.
        val targetDir = backup.targetDirectory
        if (!targetDir.isNullOrBlank() && targetDir != backup.destination) {
            DetailFieldCode(sDetail.backupsTargetDirectoryLabel, targetDir)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            DetailFieldText(sDetail.backupsSizeLabel, backup.size)
            DetailFieldText(sDetail.backupsCompressionLabel, backup.compression)
        }

        // Linked snapshots — each row is a clickable shortcut to that
        // snapshot on the Snapshots tab. The path uses `maxLines = 1` +
        // ellipsis so long paths don't blow the row's height out the way
        // they used to (paths like /Users/alice/very/long/nested/project
        // were wrapping onto two and three lines and cluttering the
        // panel).
        DetailField(sDetail.backupsIncludedSnapshotsFormat.format(linkedSnapshots.size)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                linkedSnapshots.forEach { snap ->
                    val name = snap.rootPath.split("/", "\\").last()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.02f))
                            .clickable { onNavigateToSnapshot(snap.id) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = AppColors.textMuted, modifier = Modifier.size(14.dp))
                        Text(
                            name,
                            style = AppTypography.body.copy(fontSize = 12.5.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            snap.rootPath,
                            style = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = AppColors.textDim),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = AppColors.textDim,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        // Backup history — every backup sharing this one's source
        // directory, chronological. Only rendered when there's
        // actually more than one (a solo backup's history view would
        // just be the row you're already looking at).
        if (history.size > 1) {
            BackupHistorySection(
                history = history,
                currentBackupId = backup.id,
                positionNewestFirst = positionNewestFirst,
                onSelectBackup = onSelectBackup,
            )
        }


    }

    ConfirmDeleteDialog(
        visible = confirmDelete,
        itemLabel = LocalStrings.current.itemBackup,
        itemName = backup.name,
        onConfirm = onDelete,
        onDismiss = { confirmDelete = false },
    )

    // Restore confirmation — opens when the user clicks Restore above.
    // Prepopulates the destination with the backup's original source
    // (`backup.rootPath`) so the most common case — restoring back
    // onto the original — is a single click. User can edit inline or
    // Browse to a different directory.
    RestoreBackupDialog(
        visible = showRestore,
        sourcePath = backup.rootPath,
        onConfirm = { dest -> onRestore(dest) },
        onDismiss = { showRestore = false },
    )
}

// ──────────────────────────────────────────────
// Backup History (timeline)
// ──────────────────────────────────────────────

/**
 * Vertical timeline of backups sharing the same source directory.
 *
 * The currently-selected backup is rendered with a filled accent dot
 * and a "Current" label; the others use a hollow dot and clicking a
 * row calls [onSelectBackup] to jump the detail pane to it.
 *
 *  - [history] is newest-first (what the detail panel computes above).
 *  - [positionNewestFirst] is 1-based index of the current backup in
 *    that same ordering, used for the "Position X of Y" caption.
 *  - The connecting line between dots is drawn by giving every row
 *    (except the last) a left-rail spacer that bleeds into the next row.
 */
@Composable
private fun BackupHistorySection(
    history: List<BackupUiModel>,
    /**
     * Id of the backup to mark as "current" in the timeline, or null when
     * rendering the group-level panel (no single backup is selected, so
     * every row in the timeline is equally clickable / unhighlighted).
     */
    currentBackupId: String?,
    /**
     * 1-based position of the current backup in [history]. Only shown
     * when [currentBackupId] is non-null — the group view omits it
     * because there's no position to report.
     */
    positionNewestFirst: Int?,
    onSelectBackup: (backupId: String) -> Unit,
) {
    val s = LocalStrings.current

    DetailField(s.backupsHistoryHeaderFormat.format(history.size)) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Position caption — at a glance, where this backup sits
            // in the timeline. Suppressed for the group view because
            // no single row is marked current.
            if (currentBackupId != null && positionNewestFirst != null) {
                Text(
                    s.backupsHistoryPositionFormat.format(positionNewestFirst, history.size),
                    style = AppTypography.bodySmall.copy(color = AppColors.textMuted),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            history.forEachIndexed { index, bk ->
                BackupHistoryRow(
                    backup = bk,
                    isCurrent = bk.id == currentBackupId,
                    // "Current" as a label means "most recent point in
                    // the timeline", not "the row you're looking at".
                    // history is newest-first, so index 0 is the tip.
                    isMostRecent = index == 0,
                    isLast = index == history.lastIndex,
                    onClick = { if (bk.id != currentBackupId) onSelectBackup(bk.id) },
                )
            }
        }
    }
}

@Composable
private fun BackupHistoryRow(
    backup: BackupUiModel,
    /** Selected state — drives background tint, filled dot, and enables/disables click. */
    isCurrent: Boolean,
    /** Position state — drives the "Current" pill, shown only on the newest entry. */
    isMostRecent: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val s = LocalStrings.current
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isCurrent) AppColors.accent.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .clickable(enabled = !isCurrent, onClick = onClick),
    ) {
        // Left rail — dot + connecting line. The rail's total height
        // fills the row so the line bridges cleanly into the next
        // entry without gaps.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        if (isCurrent) AppColors.accent
                        else AppColors.textDim.copy(alpha = 0.35f)
                    )
                    .then(
                        // Ring for non-current dots so the timeline reads
                        // as "milestones I can step to" rather than solid
                        // bullets.
                        if (isCurrent) Modifier
                        else Modifier.padding(1.dp)
                    ),
            )
            if (!isLast) {
                Spacer(
                    modifier = Modifier
                        .width(2.dp)
                        .height(28.dp)
                        .background(AppColors.borderLight),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp, top = 6.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    dateFormatter.format(backup.createdAt.toJavaInstant()),
                    style = AppTypography.body.copy(
                        fontSize = 12.5.sp,
                        color = if (isCurrent) AppColors.accentLight else AppColors.textSecondary,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isMostRecent) {
                    SingleLineText(
                        s.backupsHistoryCurrent,
                        style = AppTypography.chip.copy(color = AppColors.accentLight),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AppColors.accentBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SingleLineText(
                    timeAgo(backup.createdAt),
                    style = AppTypography.codeSmall.copy(color = AppColors.textDim),
                )
                SingleLineText(
                    backup.size,
                    style = AppTypography.codeSmall.copy(color = AppColors.textDim),
                )
            }
        }
    }
}

// ──────────────────────────────────────────────
// Group History Panel (no single backup selected)
// ──────────────────────────────────────────────

/**
 * Detail pane shown when the user clicks the *grouped parent* of a
 * multi-backup directory in the list.
 *
 * Strips the panel down to just the history timeline — there's no single
 * backup selected, so individual-backup fields (Status, Destination,
 * Created, etc.) don't apply. Clicking any row in the timeline promotes
 * the standard per-backup detail view via [onSelectBackup].
 */
@Composable
private fun BackupGroupHistoryPanel(
    rootPath: String,
    allBackups: List<BackupUiModel>,
    /** Current schedules — same list the list-row flow sees. */
    attachedSchedules: List<org.open.file.ui.data.BackupSchedule>,
    onClose: () -> Unit,
    onSelectBackup: (backupId: String) -> Unit,
    /** Trigger a new backup using [rootPath] — the group's shared source. */
    onNewFromSameSource: () -> Unit,
    onDeleteGroup: (alsoDeleteSchedules: Boolean) -> Unit,
) {
    val s = LocalStrings.current
    val history = remember(rootPath, allBackups) {
        allBackups.filter { it.rootPath.pathEquals(rootPath) }.sortedByDescending { it.createdAt }
    }
    val nativeHistory = remember(history) { history.filter { it.backend == BackupBackend.NATIVE } }
    // Fall back to the raw path when it has no last segment (root-level
    // directory on Unix, C:\ on Windows, etc.).
    val displayName = java.io.File(rootPath).name.ifBlank { rootPath }
    // Bulk-delete gate state. Only surfaced when there's at least one
    // native row to delete (restic writes aren't implemented).
    var showBulkConfirm by remember(rootPath) { mutableStateOf(false) }
    var alsoDeleteSchedules by remember(rootPath, attachedSchedules.size) {
        mutableStateOf(attachedSchedules.isNotEmpty())
    }

    DetailPanel(
        onClose = onClose,
        header = {
            Column {
                Text(
                    s.backupsGroupHistoryTitle,
                    style = TextStyle(fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, color = AppColors.textDim),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    displayName,
                    style = AppTypography.pageTitle.copy(fontSize = 15.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    ) {
        // Actions at the top — mirrors BackupDetailPanel's layout so
        // both single-backup and group-history views expose their
        // verbs in the same place. New From Same Source on the first
        // row, Delete All on its own row below, matching the "forward
        // actions together, destructive on its own line" split in the
        // single-backup pane — also stops the long "Delete all N
        // backups" label from crushing the New button's label when
        // both share a narrow detail pane.
        if (nativeHistory.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    s.backupsNewFromSameSource,
                    Icons.Default.Replay,
                    onClick = onNewFromSameSource,
                    backgroundColor = AppColors.accentBg,
                    borderColor = AppColors.accentBorder,
                    color = AppColors.accentLight,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // DeleteSweep icon visually signals "clears everything"
                // vs the regular trash which applies to a single row.
                DangerButton(
                    s.backupsBulkDeleteButtonFormat.format(nativeHistory.size),
                    Icons.Default.DeleteSweep,
                    onClick = { showBulkConfirm = true },
                )
            }
        }

        // Source path — the only other field worth surfacing since
        // several backups share it. Uses DetailFieldCode for parity
        // with the single-backup pane's destination chip.
        DetailFieldCode(s.snapshotsRootPathLabel, rootPath)

        // History timeline. Null current id means "no row is
        // highlighted" — every row is equally clickable since the user
        // hasn't drilled into any single backup yet.
        BackupHistorySection(
            history = history,
            currentBackupId = null,
            positionNewestFirst = null,
            onSelectBackup = onSelectBackup,
        )
    }

    // Type-to-confirm dialog rendered outside DetailPanel's body so
    // it layers above the pane rather than scrolling with it.
    TypeToConfirmDialog(
        visible = showBulkConfirm,
        title = s.backupsBulkDeleteDialogTitle,
        headlineMessage = s.backupsBulkDeleteHeadlineFormat.format(nativeHistory.size, displayName),
        confirmName = displayName,
        itemPreview = nativeHistory.map { dateFormatter.format(it.createdAt.toJavaInstant()) },
        extraContent = if (attachedSchedules.isNotEmpty()) {
            {
                DeleteAttachedSchedulesCheckbox(
                    schedules = attachedSchedules,
                    checked = alsoDeleteSchedules,
                    onCheckedChange = { alsoDeleteSchedules = it },
                )
            }
        } else null,
        onConfirm = {
            showBulkConfirm = false
            onDeleteGroup(alsoDeleteSchedules && attachedSchedules.isNotEmpty())
        },
        onDismiss = { showBulkConfirm = false },
    )
}

// ──────────────────────────────────────────────
// Create Backup Dialog
// ──────────────────────────────────────────────

/**
 * Create flow. Same dialog handles both one-off backups and recurring
 * schedules — the "Run on a schedule" switch at the bottom toggles
 * between the two modes. Folding the schedule form in here removes a
 * whole sibling modal's worth of UI and keeps source / target entry
 * identical across both paths.
 *
 * Source dir is required + validated. Target dir is optional — blank
 * means "use the default archive folder". We don't validate the
 * target the same way we validate the source:
 *  - The repo creates the directory if missing, so "doesn't exist yet"
 *    shouldn't block the user.
 *  - A typo or unreachable drive will fail at archive time and bubble
 *    up through the ErrorReporter toast, which is the right layer.
 *
 * Schedule cadence offers two sub-modes:
 *  - Presets chip row (hourly, 6h, 12h, daily, weekly) — one click,
 *    maps to a fixed interval. Covers >90% of real use.
 *  - Cron expression — 5-field POSIX syntax (see [CronExpression]).
 *    Live-validated; the submit button stays disabled until the
 *    expression parses, with the parser's error rendered inline.
 */
@Composable
private fun CreateBackupDialog(
    visible: Boolean,
    /**
     * Pre-fill for the Target field. Null / blank leaves the field
     * empty and the placeholder visible. The user can still clear it
     * or overwrite it per-backup — this is only a convenience to
     * avoid retyping the same path every time.
     */
    defaultTargetDirectory: String?,
    onDismiss: () -> Unit,
    onCreate: (sourcePath: String, targetDirectory: String?, includeHidden: Boolean) -> Unit,
    onCreateSchedule: (ScheduleSpec) -> Unit,
) {
    var path by remember { mutableStateOf("") }
    // Seed target with the configured default — `remember(key)` so a
    // settings change while the dialog is in the composition tree
    // (e.g. the Settings dialog was also open) refreshes the seed on
    // the next open without stomping in-progress edits.
    var target by remember(defaultTargetDirectory) { mutableStateOf(defaultTargetDirectory.orEmpty()) }
    // Include-hidden toggle. Default-on to preserve the pre-toggle
    // "back everything up" behaviour — users who want to exclude
    // `.git`, `.cache`, `.idea`, etc. flip it off explicitly.
    var includeHidden by remember { mutableStateOf(true) }
    // Schedule-mode state. All three persist across toggles so the
    // user can flip the switch off → back on without losing their
    // choice of preset / cron text.
    var scheduleOn by remember { mutableStateOf(false) }
    var cadenceMode by remember { mutableStateOf(CadenceMode.PRESET) }
    var intervalMinutes by remember { mutableStateOf(BackupSchedule.PRESETS.first { it.first == "Daily" }.second) }
    var cronText by remember { mutableStateOf("0 0 * * *") }

    val validation = remember(path) { validateDirectory(path) }
    val pathValid = validation is PathValidation.Valid

    // Cron validation — parse on every keystroke. Cheap (bitset build
    // at most) and lets the submit button + help text react live. Key
    // the remember on every input it reads so toggling scheduleOn or
    // the cadence mode doesn't leave a stale Result hanging around
    // (which would wedge canSubmit=false after switching back to a
    // one-off backup).
    val cronParsed = remember(cronText, scheduleOn, cadenceMode) {
        if (scheduleOn && cadenceMode == CadenceMode.CRON) {
            runCatching { org.open.file.ui.util.CronExpression.parse(cronText) }
        } else null
    }
    val cronOk = cronParsed?.isSuccess ?: true

    val canSubmit = pathValid && (!scheduleOn || cronOk)

    val resetAndDismiss: () -> Unit = {
        path = ""
        // Reset target back to the configured default — a blank reset
        // would lose the pref on every dismiss and force the user to
        // retype, which is exactly what the pref exists to avoid.
        target = defaultTargetDirectory.orEmpty()
        scheduleOn = false
        cadenceMode = CadenceMode.PRESET
        intervalMinutes = BackupSchedule.PRESETS.first { it.first == "Daily" }.second
        cronText = "0 0 * * *"
        onDismiss()
    }

    AppDialog(
        visible = visible,
        onDismiss = resetAndDismiss,
        title = if (scheduleOn) "Schedule Backup" else "Create Backup",
    ) {
        // Source directory — required.
        AppTextField(
            value = path,
            onValueChange = { path = it },
            label = "Source Directory",
            placeholder = org.open.file.ui.util.PathHints.exampleBackupSource,
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            ActionButton(
                "Browse…", Icons.Default.FolderOpen,
                onClick = {
                    pickDirectory(
                        title = "Choose Directory to Back Up",
                        startDirectory = path.ifBlank { null },
                    )?.let { chosen -> path = chosen }
                },
            )
        }

        PathStatusLine(validation)

        // Target directory — optional.
        AppTextField(
            value = target,
            onValueChange = { target = it },
            label = "Target Directory (optional)",
            placeholder = org.open.file.ui.util.PathHints.defaultBackupTargetDir,
            helpText = "Where the archive file will be written. Leave blank to use the default.",
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            ActionButton(
                "Browse…", Icons.Default.FolderOpen,
                onClick = {
                    pickDirectory(
                        title = "Choose Target Directory",
                        startDirectory = target.ifBlank { null },
                    )?.let { chosen -> target = chosen }
                },
            )
        }

        // Include-hidden toggle — whole row is the click target for
        // desktop-friendly ergonomics. Default-on to preserve the
        // pre-toggle "back everything up" behaviour; the help line
        // spells out what "off" actually excludes so users know
        // they're not dropping, e.g., `.env` files accidentally.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.02f))
                .clickable { includeHidden = !includeHidden }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                if (includeHidden) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (includeHidden) AppColors.accentLight else AppColors.textMuted,
                modifier = Modifier.size(16.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Include hidden files and folders", style = AppTypography.body)
                Text(
                    if (includeHidden) {
                        "On — `.git`, `.env`, `.idea`, etc. are archived."
                    } else {
                        "Off — dotfiles and dotdirs skipped. Useful to avoid `.git/` bloat."
                    },
                    style = AppTypography.bodySmall.copy(color = AppColors.textDim),
                )
            }
        }

        // Schedule toggle — click the whole row to flip it so the
        // target is comfortable on a desktop mouse.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (scheduleOn) AppColors.accentBg
                    else Color.White.copy(alpha = 0.02f),
                )
                .clickable { scheduleOn = !scheduleOn }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                if (scheduleOn) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (scheduleOn) AppColors.accentLight else AppColors.textMuted,
                modifier = Modifier.size(16.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Run on a schedule", style = AppTypography.body)
                Text(
                    if (scheduleOn) "This will create a recurring schedule instead of running now."
                    else "Off — clicking Create runs the backup once, now.",
                    style = AppTypography.bodySmall.copy(color = AppColors.textDim),
                )
            }
        }

        if (scheduleOn) {
            // Cadence mode picker — Preset vs Cron. Renders as two
            // pill buttons that radio-toggle.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CadenceModeChip(
                    label = "Presets",
                    selected = cadenceMode == CadenceMode.PRESET,
                    onClick = { cadenceMode = CadenceMode.PRESET },
                )
                CadenceModeChip(
                    label = "Cron",
                    selected = cadenceMode == CadenceMode.CRON,
                    onClick = { cadenceMode = CadenceMode.CRON },
                )
            }

            when (cadenceMode) {
                CadenceMode.PRESET -> {
                    // Fixed-cadence preset chips — same list the
                    // Schedules modal uses. Wrap with FlowRow-esque
                    // behaviour via spacedBy + weight so narrow
                    // windows get a clean multi-line wrap.
                    // Horizontal scroll rather than FlowRow so we can
                    // stay off the experimental layout opt-in. Narrow
                    // windows get a swipe; wide ones show every chip.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    ) {
                        BackupSchedule.PRESETS.forEach { (label, minutes) ->
                            val selected = intervalMinutes == minutes
                            Text(
                                label,
                                style = AppTypography.chip.copy(
                                    color = if (selected) AppColors.accentLight else AppColors.textSecondary,
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) AppColors.accentBg
                                        else AppColors.surfaceVariant.copy(alpha = 0.4f),
                                    )
                                    .clickable { intervalMinutes = minutes }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
                CadenceMode.CRON -> {
                    // Cron input + live validation. Below the field
                    // we show either the parser error or a preview
                    // of the next fire time so the user has feedback
                    // while they type.
                    AppTextField(
                        value = cronText,
                        onValueChange = { cronText = it },
                        label = "Cron Expression",
                        placeholder = "0 0 * * *",
                        helpText = "Minute Hour Day-of-month Month Day-of-week. Supports *, N, N-M, */N, lists.",
                    )
                    val error = cronParsed?.exceptionOrNull()
                    if (error != null) {
                        Text(
                            "Invalid: ${error.message ?: "parse failed"}",
                            style = AppTypography.bodySmall.copy(color = AppColors.error),
                        )
                    } else {
                        val nextMs = cronParsed?.getOrNull()?.nextAfter(System.currentTimeMillis())
                        val nextLabel = nextMs?.let {
                            java.time.format.DateTimeFormatter
                                .ofPattern("MMM d, yyyy h:mm a")
                                .withZone(java.time.ZoneId.systemDefault())
                                .format(java.time.Instant.ofEpochMilli(it))
                        } ?: "—"
                        Text(
                            "Next run: $nextLabel",
                            style = AppTypography.bodySmall.copy(color = AppColors.textDim),
                        )
                    }

                    // One-click preset loaders so users don't have
                    // to memorise cron for common cases.
                    // Horizontal scroll rather than FlowRow so we can
                    // stay off the experimental layout opt-in. Narrow
                    // windows get a swipe; wide ones show every chip.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    ) {
                        org.open.file.ui.util.CronExpression.PRESETS.forEach { (label, expr) ->
                            Text(
                                label,
                                style = AppTypography.chip.copy(color = AppColors.textSecondary),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AppColors.surfaceVariant.copy(alpha = 0.4f))
                                    .clickable { cronText = expr }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
        }

        Text(
            if (scheduleOn) {
                "A recurring schedule is persisted locally; the app checks every 30s for due runs " +
                        "and fires through the same archive pipeline as a manual backup."
            } else {
                "The directory will be compressed to a .zip under the target folder " +
                        "(default: ~/.open-file/backups/), and a matching snapshot recorded."
            },
            style = AppTypography.bodySmall.copy(color = AppColors.textDim),
        )

        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            ActionButton("Cancel", Icons.Default.Close, onClick = resetAndDismiss)
            Spacer(Modifier.width(8.dp))
            ActionButton(
                if (scheduleOn) "Add Schedule" else "Create Backup",
                if (scheduleOn) Icons.Default.Schedule else Icons.Default.Add,
                enabled = canSubmit,
                onClick = {
                    // Belt-and-braces: `enabled = canSubmit` already
                    // gates the click, but guard the body too so a
                    // stale click event can't slip past a transient
                    // validation failure.
                    if (canSubmit) {
                        val src = path.trim()
                        val tgt = target.trim().ifBlank { null }
                        if (scheduleOn) {
                            val spec = when (cadenceMode) {
                                CadenceMode.PRESET -> ScheduleSpec.Interval(
                                    sourcePath = src,
                                    targetDirectory = tgt,
                                    includeHidden = includeHidden,
                                    intervalMinutes = intervalMinutes,
                                )
                                CadenceMode.CRON -> ScheduleSpec.Cron(
                                    sourcePath = src,
                                    targetDirectory = tgt,
                                    includeHidden = includeHidden,
                                    expression = cronText.trim(),
                                )
                            }
                            onCreateSchedule(spec)
                        } else {
                            onCreate(src, tgt, includeHidden)
                        }
                    }
                },
                backgroundColor = AppColors.accentBg,
                borderColor = AppColors.accentBorder,
                color = AppColors.accentLight,
            )
        }
    }
}

/** Preset vs Cron sub-mode in the schedule section of [CreateBackupDialog]. */
private enum class CadenceMode { PRESET, CRON }

@Composable
private fun CadenceModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = AppTypography.chip.copy(
            color = if (selected) AppColors.accentLight else AppColors.textSecondary,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) AppColors.accentBg
                else Color.White.copy(alpha = 0.03f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * Checkbox rendered inside [TypeToConfirmDialog.extraContent] when
 * the group being bulk-deleted has matching [BackupSchedule]s. When
 * checked (default-on), the caller's onConfirm lambda routes through
 * Main.kt's scheduler-aware cleanup path so the schedules that
 * produced the runaway backups don't survive the delete.
 *
 * Clicking anywhere on the row flips the state — the whole row is
 * the target, not just the 20×20 checkbox glyph.
 */
@Composable
private fun DeleteAttachedSchedulesCheckbox(
    schedules: List<org.open.file.ui.data.BackupSchedule>,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            androidx.compose.material3.Icon(
                if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (checked) AppColors.accentLight else AppColors.textMuted,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SingleLineText(
                    "Delete ${schedules.size} attached schedule" +
                        if (schedules.size == 1) "" else "s",
                    style = AppTypography.body.copy(
                        fontSize = 13.sp,
                        color = AppColors.textSecondary,
                    ),
                )
                // Preview of the attached schedules' cadences so the
                // user can verify what they're about to turn off.
                // Kept to a single line joined by " · " — typically
                // one or two schedules per source path, rarely more.
                SingleLineText(
                    schedules.joinToString(" · ") { it.cadenceLabel() },
                    style = AppTypography.bodySmall.copy(color = AppColors.textDim),
                )
            }
        }
    }
}
