package org.open.file.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.open.file.ui.theme.AppColors
import org.open.file.ui.theme.AppTypography

/**
 * "You already have a schedule for this source / target — add another?"
 * modal. Shown when `Main.kt`'s create-schedule pipeline detects an
 * existing schedule with the same sourcePath + targetDirectory.
 *
 * The user can still proceed — double-scheduling is sometimes
 * intentional (e.g. hourly incremental + weekly full on the same
 * directory) — but surfacing the existing row's cadence first catches
 * the common case of forgetting a schedule was already set up.
 *
 * Structurally a sibling of [IdenticalBackupDialog]: an info glyph, a
 * main line, a softer detail line listing the existing cadence, and a
 * Cancel / Add Anyway button pair. Accent-tinted confirm button since
 * proceeding isn't destructive — just redundant.
 */
@Composable
fun DuplicateScheduleDialog(
    visible: Boolean,
    /** Short name (last path segment) of the source directory both schedules target. */
    sourceName: String,
    /** Existing schedule's cadence — e.g. "Daily" or "cron: 0 * * * *". */
    existingCadence: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        visible = visible,
        onDismiss = onDismiss,
        title = "Duplicate schedule?",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = AppColors.accentLight,
                modifier = Modifier.size(22.dp),
            )
            Text(
                // Primary message — hand-formatted because schedule
                // wording isn't in the i18n strings yet; a future
                // pass can promote this to `Strings` alongside its
                // ES / FR translations.
                "\"$sourceName\" is already scheduled ($existingCadence). Add another schedule for the same source and target?",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.textPrimary,
                ),
            )
        }
        Text(
            "Multiple schedules on the same path run independently — each fires its archive when its cadence is due.",
            style = AppTypography.bodySmall.copy(color = AppColors.textDim),
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            ActionButton("Cancel", Icons.Default.Close, onClick = onDismiss)
            Spacer(Modifier.width(8.dp))
            ActionButton(
                "Add Anyway",
                Icons.Default.Schedule,
                onClick = {
                    // Dismiss before firing the confirm callback so
                    // the caller's follow-up (toast, list reload)
                    // isn't racing with a still-visible modal — same
                    // pattern IdenticalBackupDialog uses.
                    onDismiss()
                    onConfirm()
                },
                backgroundColor = AppColors.accentBg,
                borderColor = AppColors.accentBorder,
                color = AppColors.accentLight,
            )
        }
    }
}
