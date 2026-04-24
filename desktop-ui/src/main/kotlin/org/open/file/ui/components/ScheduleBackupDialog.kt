package org.open.file.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.open.file.ui.data.BackupSchedule
import org.open.file.ui.theme.AppColors
import org.open.file.ui.theme.AppTypography
import org.open.file.ui.util.handOnHover
import java.time.Instant as JavaInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Viewer + manager for existing recurring backup schedules. Creation
 * moved into the main Create-Backup dialog (flip "Run on a schedule"
 * on), so this screen is intentionally scope-trimmed to list / pause-
 * resume / delete.
 *
 * Keeping the creation form out of this dialog avoids two places with
 * subtle behavioural drift over time — one source-of-truth for how a
 * schedule gets built, and no duplicated cron-validation or preset UI.
 */
@Composable
fun ScheduleBackupDialog(
    visible: Boolean,
    schedules: List<BackupSchedule>,
    onDelete: (id: String) -> Unit,
    onToggleEnabled: (id: String, enabled: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    AppDialog(
        visible = true,
        onDismiss = onDismiss,
        title = "Scheduled Backups",
    ) {
        if (schedules.isEmpty()) {
            Text(
                "No schedules yet. Open New Backup and switch on \"Run on a schedule\" " +
                        "to add one.",
                style = AppTypography.bodySmall.copy(color = AppColors.textDim),
            )
        } else {
            // Same fixed-height container we used pre-trim so a long
            // schedule list can't push the Close button off-screen.
            // LazyColumnWithScrollbar puts a visible bar on the right
            // so users know more rows exist when the list overflows.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
            ) {
                LazyColumnWithScrollbar {
                    items(schedules, key = { it.id }) { s ->
                        // 6dp gap between rows — LazyColumn can't
                        // take a verticalArrangement through our
                        // helper without adding a param, so pad the
                        // row bottoms instead.
                        Box(modifier = Modifier.padding(bottom = 6.dp)) {
                            ScheduleRow(
                                schedule = s,
                                onDelete = { onDelete(s.id) },
                                onToggleEnabled = { enabled -> onToggleEnabled(s.id, enabled) },
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            ActionButton("Close", Icons.Default.Close, onClick = onDismiss)
        }
    }
}

/**
 * One row per schedule — path, cadence, next-run, enable toggle, delete.
 */
@Composable
private fun ScheduleRow(
    schedule: BackupSchedule,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    val sourceName = java.io.File(schedule.sourcePath).name.ifBlank { schedule.sourcePath }
    val cadenceLabel = schedule.cadenceLabel()
    val nextRunLabel = formatInstant(schedule.nextRunAt)
    val lastRunLabel = schedule.lastRunAt?.let { formatInstant(it) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.surfaceVariant.copy(alpha = 0.4f))
            .border(1.dp, AppColors.borderLight.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        // Source + cadence stack.
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = AppColors.accentLight,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    sourceName,
                    style = AppTypography.rowTitle.copy(
                        color = if (schedule.enabled) AppColors.textPrimary else AppColors.textDim,
                    ),
                    maxLines = 1,
                )
                Text(
                    cadenceLabel,
                    style = AppTypography.chip.copy(color = AppColors.accentLight),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppColors.accentBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(
                schedule.sourcePath,
                style = AppTypography.code.copy(color = AppColors.textDim),
                maxLines = 1,
            )
            // Next / last run captions. "Paused" replaces next-run when
            // disabled — no point confusing the user with a timestamp
            // the scheduler won't honour.
            Text(
                if (schedule.enabled) {
                    "Next: $nextRunLabel" + (lastRunLabel?.let { " · Last: $it" } ?: "")
                } else {
                    "Paused" + (lastRunLabel?.let { " · Last: $it" } ?: "")
                },
                style = AppTypography.bodySmall.copy(color = AppColors.textDim),
            )
        }

        // Enable / disable toggle.
        IconButton(
            onClick = { onToggleEnabled(!schedule.enabled) },
            modifier = Modifier.size(28.dp).handOnHover(),
        ) {
            Icon(
                if (schedule.enabled) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (schedule.enabled) "Pause schedule" else "Resume schedule",
                tint = if (schedule.enabled) AppColors.warning else AppColors.success,
                modifier = Modifier.size(16.dp),
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(28.dp).handOnHover(),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete schedule",
                tint = AppColors.error,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ──────────────────────────────────────────────
// Formatting helpers
// ──────────────────────────────────────────────

private val runFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault())

private fun formatInstant(epochMs: Long): String =
    runFormatter.format(JavaInstant.ofEpochMilli(epochMs))
