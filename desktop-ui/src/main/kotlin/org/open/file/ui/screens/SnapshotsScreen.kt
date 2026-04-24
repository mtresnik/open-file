package org.open.file.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.open.file.snapshot.models.SavedSnapshot
import org.open.file.snapshot.store.domain.*
import org.open.file.ui.components.*
import org.open.file.ui.i18n.LocalStrings
import org.open.file.ui.state.AppState
import org.open.file.ui.theme.*
import org.open.file.ui.util.PathEquality
import org.open.file.ui.util.PathValidation
import org.open.file.ui.util.handOnHover
import org.open.file.ui.util.pathEquals
import org.open.file.ui.util.pickDirectory
import org.open.file.ui.util.validateDirectory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.toJavaInstant

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a").withZone(ZoneId.systemDefault())

private fun formatInstant(instant: Instant): String = dateFormatter.format(instant)

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1048576 -> "${"%.1f".format(bytes / 1024.0)} KB"
    else -> "${"%.1f".format(bytes / 1048576.0)} MB"
}

data class SnapshotStats(val files: Int, val dirs: Int, val totalSize: Long)

fun countNodes(node: SnapshotNode): SnapshotStats = when (node) {
    is FileNode -> SnapshotStats(1, 0, node.size)
    is DirectoryNode -> {
        var f = 0; var d = 1; var s = 0L
        node.children.forEach { val c = countNodes(it); f += c.files; d += c.dirs; s += c.totalSize }
        SnapshotStats(f, d, s)
    }
}

// ──────────────────────────────────────────────
// Snapshot List + Detail
// ──────────────────────────────────────────────

@Composable
fun SnapshotsScreen(
    state: AppState,
    snapshots: List<SavedSnapshot>,
    /** Map snapshot ID -> root SnapshotNode (loaded on demand or eagerly) */
    rootNodes: Map<String, SnapshotNode>,
    /**
     * Snapshot id → backup id, for every snapshot that's currently pinned
     * by a backup. Replaced the previous Set<String> because the list
     * needs a lock icon AND the detail pane needs a clickable way to walk
     * up to the linked backup — both require knowing which backup owns
     * the snapshot, not just that one does.
     */
    snapshotToBackup: Map<String, String>,
    onCreateSnapshot: (rootPath: String) -> Unit,
    onDeleteSnapshot: (snapshotId: String) -> Unit,
    /**
     * Bulk-delete every snapshot sharing [rootPath] — fires only after
     * the user has cleared the type-to-confirm gate. Main.kt iterates
     * matching rows through the normal delete pipeline so each row's
     * node-tree + DB row cleanup runs identically to single deletes.
     * Locked-by-backup snapshots are skipped by the caller — the
     * dialog's preview hides them so the user sees exactly what will
     * be removed.
     */
    onDeleteSnapshotGroup: (rootPath: String) -> Unit,
    /** Cross-tab shortcut — activate Backups tab and select [backupId]. */
    onNavigateToBackup: (backupId: String) -> Unit,
) {
    // Inline filter — same Compose footgun reasoning as BackupsScreen:
    // `snapshots` is a SnapshotStateList whose reference is stable
    // across add/remove, so wrapping this in `remember(snapshots, …)`
    // would cache stale contents until some other key changed.
    // distinctBy { id } defends against a duplicate landing in the
    // state list — shouldn't happen in normal flow, but Compose's
    // LazyColumn throws a loud "Key X is already used" crash the
    // moment it does, which e.g. a scheduled-backup-plus-manual-
    // create race (same UUID surfacing twice while the scheduler
    // completion path and an initial-load refresh overlap) would
    // otherwise trip. The dedup is cheap on the list sizes we see.
    val filtered: List<SavedSnapshot> = snapshots.distinctBy { it.id }.let { deduped ->
        if (state.filterText.isBlank()) {
            deduped
        } else {
            val q = state.filterText.lowercase()
            deduped.filter { it.rootPath.lowercase().contains(q) || it.id.lowercase().contains(q) }
        }
    }

    val selectedSnapshot = filtered.find { it.id == state.selectedSnapshotId }
    val selectedNode = selectedSnapshot?.let { rootNodes[it.id] }
    val selectedGroupPath = state.selectedSnapshotGroupPath

    // Create modal
    var showCreate by remember { mutableStateOf(false) }

    // Group by source path — solo groups render as plain rows, multi
    // groups render as expandable parents. Sorted so the group with
    // the newest snapshot floats to the top.
    // Group by normalised rootPath so case-variant duplicates on
    // Windows (C:\Users\Mike vs c:\users\mike) collapse into one
    // group. Display string comes from the most recent member so
    // the casing matches what the user most recently typed.
    val groups = filtered
        .groupBy { PathEquality.normalize(it.rootPath) }
        .toList()
        .map { (_, ss) ->
            val sorted = ss.sortedByDescending { it.createdAt }
            sorted.first().rootPath to sorted
        }
        .sortedByDescending { (_, ss) -> ss.first().createdAt }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    val s = LocalStrings.current
    Column(Modifier.fillMaxSize()) {
        HeaderBar(
            title = s.snapshotsTitle,
            subtitle = s.snapshotsSubtitleFormat.format(
                snapshots.size,
                snapshots.map { it.rootPath }.toSet().size,
            ),
            filterText = state.filterText,
            onFilterChange = { state.filterText = it },
            filterPlaceholder = s.snapshotsSearchPlaceholder,
            onCreateClick = { showCreate = true },
            createLabel = s.actionNew,
        )

        Row(Modifier.fillMaxSize()) {
            // List
            if (filtered.isEmpty()) {
                EmptyState(s.snapshotsEmpty)
            } else {
                LazyColumnWithScrollbar(modifier = Modifier.weight(1f)) {
                    groups.forEach { (rootPath, group) ->
                        if (group.size == 1) {
                            val snap = group[0]
                            item(key = "single-${snap.id}") {
                                StandardSnapshotRow(
                                    snap = snap,
                                    node = rootNodes[snap.id],
                                    lockedByBackup = snap.id in snapshotToBackup,
                                    selected = state.selectedSnapshotId == snap.id,
                                    onClick = { state.selectSnapshot(snap.id) },
                                    onDelete = { onDeleteSnapshot(snap.id) },
                                )
                            }
                        } else {
                            val isExpanded = expanded[rootPath] == true
                            item(key = "group-$rootPath") {
                                SnapshotGroupHeaderRow(
                                    rootPath = rootPath,
                                    group = group,
                                    // If ANY snapshot in the group is pinned
                                    // by a backup, mark the whole group with
                                    // a lock glyph so the relationship reads
                                    // from the list without drilling in.
                                    lockedByBackup = group.any { it.id in snapshotToBackup },
                                    expanded = isExpanded,
                                    selected = selectedGroupPath == rootPath,
                                    onClick = {
                                        // Auto-open on selection (matches the
                                        // Backups screen's behaviour) so the
                                        // children appear immediately; the
                                        // chevron still toggles independently.
                                        expanded[rootPath] = true
                                        state.selectSnapshotGroup(rootPath)
                                    },
                                    onToggleExpand = { expanded[rootPath] = !isExpanded },
                                )
                            }
                            if (isExpanded) {
                                items(group, key = { "child-${it.id}" }) { snap ->
                                    ChildSnapshotRow(
                                        snap = snap,
                                        node = rootNodes[snap.id],
                                        lockedByBackup = snap.id in snapshotToBackup,
                                        selected = state.selectedSnapshotId == snap.id,
                                        onClick = { state.selectSnapshot(snap.id) },
                                        onDelete = { onDeleteSnapshot(snap.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Detail pane dispatch — same three-state shape as backups.
            if (selectedSnapshot != null) {
                val linkedBackupId = snapshotToBackup[selectedSnapshot.id]
                SnapshotDetailPanel(
                    selectedSnapshot,
                    selectedNode,
                    allSnapshots = snapshots,
                    snapshotToBackup = snapshotToBackup,
                    // Non-null when a backup pins this snapshot — detail
                    // pane uses it to disable Delete AND render the
                    // clickable "Used by backup" shortcut.
                    linkedBackupId = linkedBackupId,
                    onClose = { state.selectSnapshot(null) },
                    onDelete = { onDeleteSnapshot(selectedSnapshot.id) },
                    onNavigateToBackup = onNavigateToBackup,
                    onSelectSnapshot = { sid -> state.selectSnapshot(sid) },
                )
            } else if (selectedGroupPath != null) {
                SnapshotGroupHistoryPanel(
                    rootPath = selectedGroupPath,
                    allSnapshots = snapshots,
                    snapshotToBackup = snapshotToBackup,
                    onClose = { state.selectSnapshotGroup(null) },
                    onSelectSnapshot = { sid -> state.selectSnapshot(sid) },
                    onDeleteGroup = { onDeleteSnapshotGroup(selectedGroupPath) },
                )
            }
        }
    }

    // Create Snapshot dialog
    CreateSnapshotDialog(
        visible = showCreate,
        onDismiss = { showCreate = false },
        onCreate = { path -> onCreateSnapshot(path); showCreate = false }
    )
}

// ──────────────────────────────────────────────
// Snapshot Detail Panel
// ──────────────────────────────────────────────

@Composable
private fun SnapshotDetailPanel(
    snapshot: SavedSnapshot,
    rootNode: SnapshotNode?,
    /**
     * The full snapshot list — used to build the same-rootPath history
     * timeline at the bottom of the pane. Sharing the state list from
     * the calling screen avoids a second repository read.
     */
    allSnapshots: List<SavedSnapshot>,
    /** Snapshot id → backup id map, passed through so history rows can show lock icons. */
    snapshotToBackup: Map<String, String>,
    linkedBackupId: String?,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onNavigateToBackup: (String) -> Unit,
    /** Swap the detail pane's focus to another snapshot in the same history group. */
    onSelectSnapshot: (String) -> Unit,
) {
    val lockedByBackup = linkedBackupId != null
    // History — every snapshot sharing this one's source directory,
    // newest first. Used for the timeline at the bottom of the pane.
    val history = allSnapshots
        .filter { it.rootPath.pathEquals(snapshot.rootPath) }
        .sortedByDescending { it.createdAt }
    val positionNewestFirst = history.indexOfFirst { it.id == snapshot.id }.let { if (it < 0) 0 else it + 1 }
    val stats = rootNode?.let { countNodes(it) }
    val name = when (rootNode) {
        is DirectoryNode -> rootNode.name
        is FileNode -> rootNode.name
        else -> "Snapshot"
    }

    // Local confirm gating — the Delete button raises this, the confirm
    // dialog's onConfirm drops it and calls the real onDelete.
    var confirmDelete by remember(snapshot.id) { mutableStateOf(false) }

    DetailPanel(
        onClose = onClose,
        header = {
            Column {
                Text(
                    "id: ${snapshot.id}",
                    style = TextStyle(fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, color = AppColors.textDim)
                )
                Spacer(Modifier.height(4.dp))
                Text(name, style = AppTypography.pageTitle.copy(fontSize = 15.sp))
            }
        }
    ) {
        val sDetail = LocalStrings.current
        DetailFieldCode(sDetail.snapshotsRootPathLabel, snapshot.rootPath)
        DetailFieldText(sDetail.snapshotsCreatedLabel, formatInstant(snapshot.createdAt.toJavaInstant()))

        stats?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                DetailFieldText(sDetail.snapshotsFilesLabel, "${it.files}")
                DetailFieldText(sDetail.snapshotsDirsLabel, "${it.dirs}")
                DetailFieldText(sDetail.snapshotsSizeLabel, formatBytes(it.totalSize))
            }
        }

        rootNode?.let {
            DetailFieldCode(sDetail.snapshotsRootHashLabel, it.hash.take(16))
        }

        // Snapshots are immutable records — "restore" is a backup concern and
        // lives on the Backups screen. Delete tears down both the snapshot
        // header and its entire node tree (wired up in App via the repo),
        // but only if no backup is currently pinning this snapshot.
        val sIn = LocalStrings.current
        if (linkedBackupId != null) {
            // Clickable "Used by backup" row — both explains why Delete
            // is disabled and gives the user a one-click jump to the
            // owning backup so they can resolve the lock.
            DetailField(sIn.snapshotsUsedByBackupLabel) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                        .background(AppColors.warning.copy(alpha = 0.08f))
                        .clickable { onNavigateToBackup(linkedBackupId) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = AppColors.warning,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        sIn.snapshotsLockedByBackup,
                        style = AppTypography.bodySmall.copy(color = AppColors.warning),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = AppColors.warning,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DangerButton(
                sIn.actionDelete,
                Icons.Default.Delete,
                enabled = !lockedByBackup,
                onClick = { if (!lockedByBackup) confirmDelete = true },
            )
        }

        rootNode?.let {
            DetailField(sDetail.snapshotsNodeTreeLabel) {
                FileTreePanel(it)
            }
        }

        // History timeline — matches the Backups tab's. Only appears
        // when there's actually more than one snapshot for this
        // rootPath; a solo snapshot would just render the row you're
        // already looking at.
        if (history.size > 1) {
            SnapshotHistorySection(
                history = history,
                snapshotToBackup = snapshotToBackup,
                currentSnapshotId = snapshot.id,
                positionNewestFirst = positionNewestFirst,
                onSelectSnapshot = onSelectSnapshot,
            )
        }
    }

    ConfirmDeleteDialog(
        visible = confirmDelete,
        itemLabel = LocalStrings.current.itemSnapshot,
        itemName = name,
        onConfirm = onDelete,
        onDismiss = { confirmDelete = false },
    )
}

// ──────────────────────────────────────────────
// Create Snapshot Dialog
// ──────────────────────────────────────────────

@Composable
private fun CreateSnapshotDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var path by remember { mutableStateOf("") }

    // Re-run the existence / type / permission checks whenever the input
    // changes. The checks only touch file metadata (File#exists / #isDirectory
    // / #canRead), which is cheap on a local FS and acceptable to do on the
    // UI thread for a modal dialog.
    val validation = remember(path) { validateDirectory(path) }
    val canCreate = validation is PathValidation.Valid
    val s = LocalStrings.current

    AppDialog(visible = visible, onDismiss = { path = ""; onDismiss() }, title = s.snapshotsCreateTitle) {
        AppTextField(
            value = path,
            onValueChange = { path = it },
            label = s.snapshotsRootDirectoryLabel,
            // OS-specific hint only — no prepopulation. The hardcoded
            // `/home/user/…` placeholder didn't make sense on Windows.
            placeholder = org.open.file.ui.util.PathHints.exampleProjectPath,
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            ActionButton(
                s.actionBrowse, Icons.Default.FolderOpen,
                onClick = {
                    pickDirectory(
                        title = s.snapshotsRootDirectoryLabel,
                        startDirectory = path.ifBlank { null },
                    )?.let { chosen -> path = chosen }
                },
            )
        }

        PathStatusLine(validation)

        Text(
            s.snapshotsCreateHelp,
            style = AppTypography.bodySmall.copy(color = AppColors.textDim)
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            ActionButton(s.actionCancel, Icons.Default.Close, onClick = { path = ""; onDismiss() })
            Spacer(Modifier.width(8.dp))
            ActionButton(
                s.snapshotsCreateTitle, Icons.Default.Add,
                enabled = canCreate,
                onClick = { if (canCreate) onCreate(path.trim()) },
                backgroundColor = AppColors.accentBg,
                borderColor = AppColors.accentBorder,
                color = AppColors.accentLight,
            )
        }
    }
}

// ──────────────────────────────────────────────
// List rows — solo, group header, grouped child
// ──────────────────────────────────────────────

private fun deriveSnapshotName(snap: SavedSnapshot, node: SnapshotNode?): String = when (node) {
    is DirectoryNode -> node.name
    is FileNode -> node.name
    else -> snap.rootPath.split('/', '\\').last().ifBlank { snap.rootPath }
}

/**
 * Plain snapshot row — used for rootPaths that only have a single
 * snapshot. Visually identical to the pre-grouping layout. A backup-
 * pinned snapshot swaps the archive chip for a lock glyph in warning
 * colour, same as before.
 */
@Composable
private fun StandardSnapshotRow(
    snap: SavedSnapshot,
    node: SnapshotNode?,
    lockedByBackup: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val stats = node?.let { countNodes(it) }
    val name = deriveSnapshotName(snap, node)
    ListRow(
        selected = selected,
        onClick = onClick,
        icon = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .background(
                        if (lockedByBackup) AppColors.warning.copy(alpha = 0.12f)
                        else Color.White.copy(alpha = 0.03f)
                    ),
            ) {
                Icon(
                    if (lockedByBackup) Icons.Default.Lock else Icons.Default.Inventory2,
                    contentDescription = if (lockedByBackup) "Pinned by backup" else null,
                    tint = if (lockedByBackup) AppColors.warning else AppColors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    name,
                    style = AppTypography.rowTitle,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                // Hash prefix (8 chars of the UUID). Single-line
                // guarded because without it, a narrowed cell —
                // detail pane open + long filename — used to
                // character-stack the hash vertically.
                SingleLineText(
                    snap.id.take(8),
                    style = AppTypography.chip.copy(color = AppColors.textDim),
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                snap.rootPath,
                style = AppTypography.code.copy(color = AppColors.accent.copy(alpha = 0.6f)),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                stats?.let {
                    SingleLineText("${it.files}f / ${it.dirs}d", style = AppTypography.codeSmall)
                    SingleLineText(formatBytes(it.totalSize), style = AppTypography.codeSmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    SingleLineText(timeAgo(snap.createdAt), style = AppTypography.bodySmall)
                }
            }
        },
        actions = {
            // Trash is disabled for snapshots a backup pins — the
            // matching backup has to go first (the detail pane's
            // Delete button applies the same gate). Tooltip spells
            // out why, so users don't wonder why the icon is dim.
            RowDeleteIcon(
                itemLabel = "snapshot",
                itemName = java.io.File(snap.rootPath).name.ifBlank { snap.rootPath },
                onConfirm = onDelete,
                enabled = !lockedByBackup,
                disabledHint = "Pinned by a backup — delete the backup first",
            )
        },
    )
}

/**
 * Expandable parent row for a rootPath with multiple snapshots. Same
 * shape as the backups version — directory icon tile, translated
 * "N snapshots" count chip, independent expand/collapse chevron.
 * Selecting the body activates the group-history detail view.
 */
@Composable
private fun SnapshotGroupHeaderRow(
    rootPath: String,
    group: List<SavedSnapshot>,
    lockedByBackup: Boolean,
    expanded: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleExpand: () -> Unit,
) {
    val s = LocalStrings.current
    val latest = group.first()
    val displayName = rootPath.split('/', '\\').last().ifBlank { rootPath }
    ListRow(
        selected = selected,
        onClick = onClick,
        icon = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .background(
                        if (lockedByBackup) AppColors.warning.copy(alpha = 0.12f)
                        else AppColors.accent.copy(alpha = 0.10f)
                    ),
            ) {
                Icon(
                    if (lockedByBackup) Icons.Default.Lock else Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (lockedByBackup) AppColors.warning else AppColors.accentLight,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    displayName,
                    style = AppTypography.rowTitle,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                // Group count chip — same single-line guard as the
                // Backups screen's parallel chip so "5 snapshots"
                // doesn't character-stack in a narrowed cell.
                SingleLineText(
                    s.snapshotsGroupCountFormat.format(group.size),
                    style = AppTypography.chip.copy(color = AppColors.accentLight),
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .background(AppColors.accentBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                rootPath,
                style = AppTypography.code.copy(color = AppColors.accent.copy(alpha = 0.6f)),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SingleLineText(timeAgo(latest.createdAt), style = AppTypography.bodySmall)
                androidx.compose.material3.IconButton(
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
    )
}

/**
 * Indented child row beneath a group header — the distinguishing
 * attribute is the snapshot's timestamp (since every child shares the
 * directory name), so the timestamp becomes the row's primary text.
 */
@Composable
private fun ChildSnapshotRow(
    snap: SavedSnapshot,
    node: SnapshotNode?,
    lockedByBackup: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val stats = node?.let { countNodes(it) }
    Box(modifier = Modifier.padding(start = 24.dp)) {
        ListRow(
            selected = selected,
            onClick = onClick,
            icon = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                        .background(
                            if (lockedByBackup) AppColors.warning.copy(alpha = 0.12f)
                            else Color.White.copy(alpha = 0.03f)
                        ),
                ) {
                    Icon(
                        if (lockedByBackup) Icons.Default.Lock else Icons.Default.Inventory2,
                        contentDescription = if (lockedByBackup) "Pinned by backup" else null,
                        tint = if (lockedByBackup) AppColors.warning else AppColors.textMuted,
                        modifier = Modifier.size(14.dp),
                    )
                }
            },
            content = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        formatInstant(snap.createdAt.toJavaInstant()),
                        style = AppTypography.body.copy(fontSize = 12.5.sp),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    // Same hash-prefix guard as StandardSnapshotRow.
                    SingleLineText(
                        snap.id.take(8),
                        style = AppTypography.chip.copy(color = AppColors.textDim),
                    )
                }
            },
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    stats?.let {
                        SingleLineText(formatBytes(it.totalSize), style = AppTypography.codeSmall)
                    }
                    SingleLineText(timeAgo(snap.createdAt), style = AppTypography.bodySmall)
                }
            },
            actions = {
                // Mirror the standard row's lock gate. Use the
                // timestamp as the itemName so the confirm dialog
                // reads "Delete snapshot Apr 24, 5:03 PM?" — inside
                // a group the directory name is shared with every
                // sibling, so dates disambiguate.
                RowDeleteIcon(
                    itemLabel = "snapshot",
                    itemName = formatInstant(snap.createdAt.toJavaInstant()),
                    onConfirm = onDelete,
                    enabled = !lockedByBackup,
                    disabledHint = "Pinned by a backup — delete the backup first",
                )
            },
        )
    }
}

// ──────────────────────────────────────────────
// Snapshot history timeline (used by both single and group detail panes)
// ──────────────────────────────────────────────

/**
 * Chronological list of every snapshot sharing a rootPath. Mirrors the
 * Backups screen's timeline — filled dot + connecting line, "Current"
 * pill on the newest entry only, selected row tinted and non-clickable.
 *
 * [currentSnapshotId] / [positionNewestFirst] are both nullable so the
 * group-history pane can render the timeline without any row marked
 * "selected" and without a position caption (no current backup to
 * position against when the user is viewing the group as a whole).
 */
@Composable
private fun SnapshotHistorySection(
    history: List<SavedSnapshot>,
    snapshotToBackup: Map<String, String>,
    currentSnapshotId: String?,
    positionNewestFirst: Int?,
    onSelectSnapshot: (snapshotId: String) -> Unit,
) {
    val s = LocalStrings.current
    DetailField(s.snapshotsHistoryHeaderFormat.format(history.size)) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (currentSnapshotId != null && positionNewestFirst != null) {
                // Reuse the backup-history phrasing for "Position N of M"
                // — the label is generic enough to apply to either
                // timeline without needing a separate translation key.
                Text(
                    s.backupsHistoryPositionFormat.format(positionNewestFirst, history.size),
                    style = AppTypography.bodySmall.copy(color = AppColors.textMuted),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            history.forEachIndexed { index, snap ->
                SnapshotHistoryRow(
                    snap = snap,
                    pinnedByBackup = snap.id in snapshotToBackup,
                    isCurrent = snap.id == currentSnapshotId,
                    isMostRecent = index == 0,
                    isLast = index == history.lastIndex,
                    onClick = { if (snap.id != currentSnapshotId) onSelectSnapshot(snap.id) },
                )
            }
        }
    }
}

@Composable
private fun SnapshotHistoryRow(
    snap: SavedSnapshot,
    pinnedByBackup: Boolean,
    isCurrent: Boolean,
    isMostRecent: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val s = LocalStrings.current
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .background(if (isCurrent) AppColors.accent.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(enabled = !isCurrent, onClick = onClick),
    ) {
        // Left rail — same visual treatment as the backups history.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
                    .background(
                        if (isCurrent) AppColors.accent
                        else AppColors.textDim.copy(alpha = 0.35f)
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
                    formatInstant(snap.createdAt.toJavaInstant()),
                    style = AppTypography.body.copy(
                        fontSize = 12.5.sp,
                        color = if (isCurrent) AppColors.accentLight else AppColors.textSecondary,
                    ),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (pinnedByBackup) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Pinned by backup",
                        tint = AppColors.warning,
                        modifier = Modifier.size(12.dp),
                    )
                }
                if (isMostRecent) {
                    SingleLineText(
                        s.backupsHistoryCurrent,
                        style = AppTypography.chip.copy(color = AppColors.accentLight),
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .background(AppColors.accentBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SingleLineText(
                    timeAgo(snap.createdAt),
                    style = AppTypography.codeSmall.copy(color = AppColors.textDim),
                )
                SingleLineText(
                    snap.id.take(8),
                    style = AppTypography.codeSmall.copy(color = AppColors.textDim),
                )
            }
        }
    }
}

// ──────────────────────────────────────────────
// Group history detail pane (no single snapshot selected)
// ──────────────────────────────────────────────

/**
 * Right-hand pane shown when the user clicks the grouped parent of a
 * multi-snapshot directory. Stripped-down — just the source path chip
 * and the history timeline. Clicking a row in the timeline promotes
 * the standard per-snapshot detail view via [onSelectSnapshot].
 */
@Composable
private fun SnapshotGroupHistoryPanel(
    rootPath: String,
    allSnapshots: List<SavedSnapshot>,
    snapshotToBackup: Map<String, String>,
    onClose: () -> Unit,
    onSelectSnapshot: (snapshotId: String) -> Unit,
    /**
     * Bulk-delete every snapshot sharing [rootPath] that isn't
     * currently pinned by a backup. Fires only after the type-to-
     * confirm gate clears. Callers (Main.kt via SnapshotsScreen)
     * iterate matching rows through the normal delete pipeline.
     */
    onDeleteGroup: () -> Unit,
) {
    val s = LocalStrings.current
    val history = allSnapshots
        .filter { it.rootPath.pathEquals(rootPath) }
        .sortedByDescending { it.createdAt }
    // Split into deletable / locked so the bulk-delete flow can
    // report exactly what will be touched and leave locked rows
    // alone — same gating rule as single-row delete (you can't
    // nuke a snapshot a backup pins without deleting that backup
    // first).
    val deletable = history.filter { it.id !in snapshotToBackup }
    val lockedCount = history.size - deletable.size
    val displayName = rootPath.split('/', '\\').last().ifBlank { rootPath }
    // Local bulk-delete gate. Keyed on rootPath so switching groups
    // resets the dialog state.
    var showBulkConfirm by remember(rootPath) { mutableStateOf(false) }

    DetailPanel(
        onClose = onClose,
        header = {
            Column {
                Text(
                    s.snapshotsGroupHistoryTitle,
                    style = TextStyle(fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, color = AppColors.textDim),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    displayName,
                    style = AppTypography.pageTitle.copy(fontSize = 15.sp),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        },
    ) {
        // Actions at the top — mirrors BackupGroupHistoryPanel so both
        // group views expose their verbs in the same place. Delete-all
        // only when there's something unlocked to delete; an all-
        // locked group (every row pinned by a backup) hides the
        // button entirely rather than showing a disabled one.
        if (deletable.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DangerButton(
                    s.snapshotsBulkDeleteButtonFormat.format(deletable.size),
                    Icons.Default.DeleteSweep,
                    onClick = { showBulkConfirm = true },
                )
            }
            if (lockedCount > 0) {
                // Surface locked-count transparency — if the group has
                // 5 snapshots and 2 are pinned, the button says "3" and
                // this line tells the user about the 2 we're leaving
                // behind so the arithmetic reads correctly.
                Text(
                    s.snapshotsBulkDeleteLockedSomeFormat.format(lockedCount),
                    style = AppTypography.bodySmall.copy(color = AppColors.textDim),
                )
            }
        } else if (lockedCount > 0) {
            // All-locked fallback — we render no button but still tell
            // the user why, so an empty action area isn't mysterious.
            Text(
                s.snapshotsBulkDeleteLockedAll,
                style = AppTypography.bodySmall.copy(color = AppColors.textDim),
            )
        }

        DetailFieldCode(s.snapshotsRootPathLabel, rootPath)
        SnapshotHistorySection(
            history = history,
            snapshotToBackup = snapshotToBackup,
            currentSnapshotId = null,
            positionNewestFirst = null,
            onSelectSnapshot = onSelectSnapshot,
        )
    }

    // Type-to-confirm gate. Preview lists each snapshot's timestamp
    // — same approach as the backups side, except we use timestamps
    // instead of archive filenames because snapshots don't have
    // their own on-disk name.
    TypeToConfirmDialog(
        visible = showBulkConfirm,
        title = s.snapshotsBulkDeleteDialogTitle,
        headlineMessage = s.snapshotsBulkDeleteHeadlineFormat.format(deletable.size, displayName),
        confirmName = displayName,
        itemPreview = deletable.map { formatInstant(it.createdAt.toJavaInstant()) },
        onConfirm = {
            showBulkConfirm = false
            onDeleteGroup()
        },
        onDismiss = { showBulkConfirm = false },
    )
}
