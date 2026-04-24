package org.open.file.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.open.file.ui.i18n.LocalStrings
import org.open.file.ui.theme.AppColors
import org.open.file.ui.theme.AppTypography
import org.open.file.ui.util.PathHints
import org.open.file.ui.util.pickDirectory

/**
 * Two-step restore flow — replaces the previous click-Restore-and-get-
 * the-native-picker behaviour with an in-app confirmation modal.
 *
 * Features:
 *  - Destination text field prefilled with the backup's original
 *    [sourcePath] so "restore over the original" is a single click.
 *  - Inline Browse button that opens the OS directory picker and
 *    writes the chosen path back into the field (same
 *    text+button pattern the Create dialogs use).
 *  - OS-aware placeholder hint from [PathHints] for when the user
 *    clears the field.
 *  - Cancel / Restore buttons at the bottom. The Restore button
 *    disables while the field is blank and dismisses the dialog
 *    before invoking [onConfirm] so the progress toast takes over
 *    cleanly.
 *
 * The caller (BackupsScreen) owns the show/hide boolean; this
 * composable just decides what to render when visible.
 */
@Composable
fun RestoreBackupDialog(
    visible: Boolean,
    sourcePath: String,
    onConfirm: (destinationPath: String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val s = LocalStrings.current

    // Seeded on every open — `remember(sourcePath)` resets the field
    // when the dialog opens for a different backup. Users can edit
    // or replace via Browse.
    var path by remember(sourcePath) { mutableStateOf(sourcePath) }
    val trimmed = path.trim()
    val canConfirm = trimmed.isNotBlank()

    AppDialog(
        visible = true,
        onDismiss = onDismiss,
        title = s.backupsRestoreDialogTitle,
    ) {
        Text(
            s.backupsRestoreDialogHelp,
            style = AppTypography.bodySmall.copy(color = AppColors.textDim),
        )

        // Destination field with inline Browse. Laid out by hand
        // rather than reusing TemplatesScreen's PathInputField
        // (which is file-private) so we don't have to widen that
        // helper's visibility just for one more call site.
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AppTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = s.backupsRestoreDestinationLabel,
                    placeholder = PathHints.documentsDir,
                    helpText = s.backupsRestoreDestinationHelp,
                )
            }
            // Nudge the Browse button down so its baseline lines up
            // with the text-field input row instead of the uppercase
            // label above — same trick PathInputField uses.
            Box(modifier = Modifier.padding(bottom = 20.dp)) {
                ActionButton(
                    s.actionBrowse,
                    Icons.Default.FolderOpen,
                    onClick = {
                        pickDirectory(
                            title = s.backupsRestoreDialogTitle,
                            startDirectory = trimmed.ifBlank { null },
                        )?.let { chosen -> path = chosen }
                    },
                )
            }
        }

        // Show the source path as a clickable chip below the field.
        // Copying is a frequent follow-up action ("lemme paste this
        // into a terminal") so it earns click-to-copy + the same
        // inline "Copied" confirmation used by DetailFieldCode.
        var sourceCopied by remember(sourcePath) { mutableStateOf(false) }
        LaunchedEffect(sourceCopied) {
            if (sourceCopied) {
                kotlinx.coroutines.delay(1200)
                sourceCopied = false
            }
        }
        // Vertical stack so the "Copied" row lives below the chip
        // instead of competing with a long path for horizontal space.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("FROM", style = AppTypography.label)
                Text(
                    sourcePath,
                    style = AppTypography.code.copy(color = AppColors.accent.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppColors.accent.copy(alpha = 0.08f))
                        .clickable {
                            if (org.open.file.ui.util.copyToClipboard(sourcePath)) {
                                sourceCopied = true
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (sourceCopied) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Default.Check,
                        contentDescription = null,
                        tint = AppColors.success,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(s.actionCopied, style = AppTypography.bodySmall.copy(color = AppColors.success))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            ActionButton(s.actionCancel, Icons.Default.Close, onClick = onDismiss)
            Spacer(Modifier.width(8.dp))
            ActionButton(
                s.actionRestore,
                Icons.Default.Restore,
                enabled = canConfirm,
                onClick = {
                    if (canConfirm) {
                        // Dismiss first so the progress / reveal flow
                        // (which the caller triggers) isn't racing
                        // with the still-visible modal.
                        onDismiss()
                        onConfirm(trimmed)
                    }
                },
                backgroundColor = AppColors.accentBg,
                borderColor = AppColors.accentBorder,
                color = AppColors.accentLight,
            )
        }
    }
}
