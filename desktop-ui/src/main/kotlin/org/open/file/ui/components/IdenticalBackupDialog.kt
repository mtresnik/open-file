package org.open.file.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.open.file.ui.i18n.LocalStrings
import org.open.file.ui.theme.AppColors
import org.open.file.ui.theme.AppTypography

/**
 * "This backup looks identical to a previous one — continue?" modal.
 *
 * Shown by [Main.kt]'s create-backup pipeline when the quick pre-scan
 * (file count + total bytes) matches the most recent backup of the
 * same source directory. The user can still proceed — the check is
 * opt-in via the Settings toggle — or cancel to avoid the duplicate.
 *
 * Rendering mirrors [ConfirmDeleteDialog]: an info glyph with the
 * main line, a secondary detail line, and a two-button row. The
 * "proceed" button is accent-tinted rather than destructive because
 * going ahead isn't dangerous, just redundant.
 */
@Composable
fun IdenticalBackupDialog(
    visible: Boolean,
    existingName: String,
    existingTimeAgo: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    AppDialog(
        visible = visible,
        onDismiss = onDismiss,
        title = s.dialogIdenticalBackupTitle,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = AppColors.accentLight,
                modifier = Modifier.size(22.dp),
            )
            Text(
                // "This source hasn't changed since \"%s\" (%s). Create
                // another backup anyway?" — formatted with name + time-ago.
                s.dialogIdenticalBackupMessageFormat.format(existingName, existingTimeAgo),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.textPrimary),
            )
        }
        Text(
            // Softer second line so the main message stands alone when
            // users skim. Reuses the existing "you can disable this in
            // Settings" mental model from other apps.
            s.settingsWarnIdenticalHelp,
            style = AppTypography.bodySmall.copy(color = AppColors.textDim),
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            ActionButton(s.actionCancel, Icons.Default.Close, onClick = onDismiss)
            Spacer(Modifier.width(8.dp))
            ActionButton(
                s.dialogIdenticalBackupProceed,
                Icons.Default.ContentCopy,
                onClick = {
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
