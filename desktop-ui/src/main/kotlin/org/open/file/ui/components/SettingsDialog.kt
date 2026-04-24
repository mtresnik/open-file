package org.open.file.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.open.file.ui.i18n.LocalStrings
import org.open.file.ui.theme.AccentChoice
import org.open.file.ui.theme.AppColors
import org.open.file.ui.theme.AppPalette
import org.open.file.ui.theme.AppTypography
import org.open.file.ui.theme.FontChoice
import org.open.file.ui.util.handOnHover

/**
 * Sections the settings modal is split into. Order matters — this is
 * the vertical ordering of the left-rail navigation.
 */
private enum class SettingsSection { APPEARANCE, CONFIG, RESTIC, ABOUT }

/**
 * Umbrella settings dialog triggered by the sidebar's gear icon.
 *
 * Laid out as a fixed-size Dialog with a left navigation rail + right
 * content pane — the same pattern as the app's main sidebar so it
 * feels consistent. Selecting a section swaps the right-hand content
 * without dismissing the modal.
 *
 * Adding a new section is two steps:
 *   1. Add an entry to [SettingsSection] + a label in Strings.
 *   2. Add a clause to the `when(activeSection)` block that renders
 *      the section's form.
 */
@Composable
fun SettingsDialog(
    visible: Boolean,
    selectedPalette: String?,
    selectedAccent: String?,
    selectedFont: FontChoice,
    warnIdenticalBackups: Boolean,
    /**
     * Current value of the "default backup target directory" pref.
     * Blank = no preference. The Config section's text field mirrors
     * this value; edits fire [onDefaultBackupTargetDirChanged] on
     * every keystroke.
     */
    defaultBackupTargetDir: String,
    minimizeToTray: Boolean,
    runOnStartup: Boolean,
    /** Null when run-on-startup is supported on this install; non-null renders the toggle disabled. */
    runOnStartupReason: String?,
    resticRepoPath: String,
    resticPasswordFile: String,
    onPaletteSelected: (AppPalette) -> Unit,
    onAccentSelected: (AccentChoice) -> Unit,
    onFontSelected: (FontChoice) -> Unit,
    onWarnIdenticalToggled: (Boolean) -> Unit,
    /** Persist the default-target-dir pref. Fires per keystroke + from the Browse picker. Blank clears the pref. */
    onDefaultBackupTargetDirChanged: (String) -> Unit,
    onMinimizeToTrayToggled: (Boolean) -> Unit,
    onRunOnStartupToggled: (Boolean) -> Unit,
    /** Persist restic config + reload the backups list. Fires on every keystroke. */
    onResticConfigChanged: (repoPath: String, passwordFile: String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val s = LocalStrings.current

    // Selection state lives inside the dialog — callers don't care
    // which section is active, they just want their settings applied.
    // Persisted across recompositions but re-opens default to
    // Appearance, which matches user expectation for "Settings".
    var activeSection by remember { mutableStateOf(SettingsSection.APPEARANCE) }

    // Dialog width tracks the parent window: 60% of the window width,
    // clamped to [820dp, 960dp]. The 820dp floor (up from 720dp) gives
    // the right pane enough horizontal room for the Appearance
    // section's palette grid + accent row + font dropdown to sit at
    // comfortable widths even on compact windows. Height stays fixed
    // at 520dp because the right pane already scrolls.
    val metrics = org.open.file.ui.util.LocalLayoutMetrics.current
    val settingsDialogWidth = (metrics.windowWidthDp * 0.6f).coerceIn(820.dp, 960.dp)

    Dialog(
        onDismissRequest = {
            // Same grace-period guard as AppDialog — SettingsDialog
            // doesn't currently open a file picker itself, but the
            // Restic section has a password-file field that will in
            // future, and being consistent here costs nothing.
            if (org.open.file.ui.util.DialogDismissLock.canDismissNow()) {
                onDismiss()
            }
        },
    ) {
        Column(
            modifier = Modifier
                // Responsive width + fixed height: the left rail gets
                // 180dp, the rest is for the right pane which
                // scrolls if its section overflows the 520dp body.
                .width(settingsDialogWidth)
                .height(520.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.surfaceVariant)
                .border(1.dp, AppColors.borderLight, RoundedCornerShape(14.dp)),
        ) {
            // Header bar — gear icon + dialog title + close X.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                // Gear glyph alongside the title — matches the
                // sidebar's "Settings" footer button so users connect
                // the modal to the entry point they clicked from. The
                // accent tint stops the icon from reading as a muted
                // decoration; it's part of the header's hierarchy.
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = AppColors.accentLight,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    s.dialogSettingsTitle,
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDismiss,
                    // Hand cursor on hover — IconButton's internal
                    // ripple modifier doesn't set a cursor icon by
                    // default, so we layer one on at this wrapper.
                    modifier = Modifier.size(24.dp).handOnHover(),
                ) {
                    Icon(Icons.Default.Close, contentDescription = s.actionClose, tint = AppColors.textDim, modifier = Modifier.size(16.dp))
                }
            }
            Divider(color = AppColors.border, thickness = 1.dp)

            // Body: left rail + vertical divider + right content.
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // Left navigation rail. Same pattern as the main app
                // sidebar — hover + selected drive a left accent bar
                // (drawBehind, not a layout sibling).
                Column(
                    modifier = Modifier
                        .width(180.dp)
                        .fillMaxHeight()
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    SectionNavRow(
                        label = s.settingsSectionAppearance,
                        icon = Icons.Default.Palette,
                        isActive = activeSection == SettingsSection.APPEARANCE,
                        onClick = { activeSection = SettingsSection.APPEARANCE },
                    )
                    SectionNavRow(
                        label = s.settingsSectionConfig,
                        icon = Icons.Default.Tune,
                        isActive = activeSection == SettingsSection.CONFIG,
                        onClick = { activeSection = SettingsSection.CONFIG },
                    )
                    SectionNavRow(
                        label = s.settingsSectionRestic,
                        icon = Icons.Default.Cloud,
                        isActive = activeSection == SettingsSection.RESTIC,
                        onClick = { activeSection = SettingsSection.RESTIC },
                    )
                    SectionNavRow(
                        label = s.settingsSectionAbout,
                        icon = Icons.Default.Info,
                        isActive = activeSection == SettingsSection.ABOUT,
                        onClick = { activeSection = SettingsSection.ABOUT },
                    )
                }

                Divider(
                    color = AppColors.border,
                    modifier = Modifier.fillMaxHeight().width(1.dp),
                )

                // Right content pane. verticalScroll handles any section
                // taller than the dialog (currently only Appearance is
                // close to the limit with its palette grid).
                //
                // SelectionContainer makes every descendant Text
                // selectable — toggle labels, help text under
                // switches, and any static section copy. Callers who
                // want to paste a link to Anthropic's restic docs
                // or copy the "Run on Startup" help line into a
                // support thread can drag across the pane. Form
                // inputs (AppTextField) already handle their own
                // selection via BasicTextField so they're unaffected.
                androidx.compose.foundation.text.selection.SelectionContainer(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when (activeSection) {
                            SettingsSection.APPEARANCE -> ThemeControls(
                                selectedPalette = selectedPalette,
                                selectedAccent = selectedAccent,
                                selectedFont = selectedFont,
                                onPaletteSelected = onPaletteSelected,
                                onAccentSelected = onAccentSelected,
                                onFontSelected = onFontSelected,
                            )
                            SettingsSection.CONFIG -> ConfigSection(
                                warnIdenticalBackups = warnIdenticalBackups,
                                onWarnIdenticalToggled = onWarnIdenticalToggled,
                                defaultBackupTargetDir = defaultBackupTargetDir,
                                onDefaultBackupTargetDirChanged = onDefaultBackupTargetDirChanged,
                                minimizeToTray = minimizeToTray,
                                onMinimizeToTrayToggled = onMinimizeToTrayToggled,
                                runOnStartup = runOnStartup,
                                runOnStartupReason = runOnStartupReason,
                                onRunOnStartupToggled = onRunOnStartupToggled,
                            )
                            SettingsSection.RESTIC -> ResticSection(
                                resticRepoPath = resticRepoPath,
                                resticPasswordFile = resticPasswordFile,
                                onResticConfigChanged = onResticConfigChanged,
                            )
                            SettingsSection.ABOUT -> AboutSection()
                        }
                    }
                }
            }

            Divider(color = AppColors.border, thickness = 1.dp)

            // Footer — single Done button. All changes auto-apply as
            // the user edits fields, so Done is purely dismiss.
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                ActionButton(
                    s.actionDone,
                    Icons.Default.Check,
                    onClick = onDismiss,
                    backgroundColor = AppColors.accentBg,
                    borderColor = AppColors.accentBorder,
                    color = AppColors.accentLight,
                )
            }
        }
    }
}

/**
 * Nav row in the modal's left rail. Mirrors the main sidebar's nav
 * rows: hover + selected drive a 3dp left-edge accent bar via
 * drawBehind, no background tint.
 */
@Composable
private fun SectionNavRow(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val barColor = when {
        isActive -> AppColors.accent
        isHovered -> AppColors.accent.copy(alpha = 0.4f)
        else -> Color.Transparent
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .clickable(
                interactionSource = hoverSource,
                indication = null,
                onClick = onClick,
            )
            // Hand cursor on hover — nav rows are clickable surfaces
            // even though they render as flat text + accent bar
            // rather than boxed buttons, so the cursor cue matters
            // here too. Placed after `clickable` so the hover region
            // matches exactly what's interactive.
            .handOnHover()
            .drawBehind {
                if (barColor != Color.Transparent) {
                    drawRect(
                        color = barColor,
                        topLeft = Offset.Zero,
                        size = Size(3.dp.toPx(), size.height),
                    )
                }
            }
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isActive) AppColors.accentLight else AppColors.textMuted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = if (isActive) AppTypography.navItemActive else AppTypography.navItem,
            modifier = Modifier.weight(1f),
        )
    }
}

// ──────────────────────────────────────────────
// Section content
// ──────────────────────────────────────────────

/**
 * "Config" section — general behaviour toggles. Currently holds the
 * identical-backup warning; more toggles slot in by appending more
 * [SettingsToggleRow] blocks below.
 */
@Composable
private fun ConfigSection(
    warnIdenticalBackups: Boolean,
    onWarnIdenticalToggled: (Boolean) -> Unit,
    defaultBackupTargetDir: String,
    onDefaultBackupTargetDirChanged: (String) -> Unit,
    minimizeToTray: Boolean,
    onMinimizeToTrayToggled: (Boolean) -> Unit,
    runOnStartup: Boolean,
    runOnStartupReason: String?,
    onRunOnStartupToggled: (Boolean) -> Unit,
) {
    val s = LocalStrings.current
    Text(s.settingsBackupsLabel, style = AppTypography.label)
    SettingsToggleRow(
        label = s.settingsWarnIdenticalLabel,
        helpText = s.settingsWarnIdenticalHelp,
        checked = warnIdenticalBackups,
        onCheckedChange = onWarnIdenticalToggled,
    )

    // Default target directory. Prefills the Create Backup dialog's
    // Target field so users who always back up to the same drive
    // don't have to pick the path every time. Leaving it blank keeps
    // the dialog empty (archiver falls back to its own default).
    //
    // Keystroke-level persistence mirrors the Restic fields — no
    // Save button. Browse lives in a Row alongside the field, same
    // pattern as Restic's password-file picker.
    Spacer(Modifier.height(4.dp))
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            AppTextField(
                value = defaultBackupTargetDir,
                onValueChange = onDefaultBackupTargetDirChanged,
                label = s.settingsDefaultTargetDirLabel,
                placeholder = org.open.file.ui.util.PathHints.defaultBackupTargetDir,
                helpText = s.settingsDefaultTargetDirHelp,
            )
        }
        Box(modifier = Modifier.padding(bottom = 4.dp)) {
            ActionButton(
                label = s.actionBrowse,
                icon = Icons.Default.FolderOpen,
                onClick = {
                    val picked = org.open.file.ui.util.pickDirectory(
                        title = s.settingsDefaultTargetDirPickerTitle,
                        startDirectory = defaultBackupTargetDir.ifBlank { null },
                    )
                    if (picked != null) onDefaultBackupTargetDirChanged(picked)
                },
            )
        }
    }

    // Window-behaviour section. Kept under the Config umbrella rather
    // than spinning up a dedicated Behaviour section since there's
    // only one toggle here today — easy to split out later if more
    // window-level settings land.
    Spacer(Modifier.height(8.dp))
    Text(s.settingsWindowLabel, style = AppTypography.label)
    SettingsToggleRow(
        label = s.settingsMinimizeToTrayLabel,
        helpText = s.settingsMinimizeToTrayHelp,
        checked = minimizeToTray,
        onCheckedChange = onMinimizeToTrayToggled,
    )

    // Startup section — autostart on login. Disabled when we can't
    // resolve a real launcher path (e.g. running from `gradle run`,
    // where autostarting the JVM directly wouldn't bring the UI up).
    // The reason string replaces the normal helpText so the user
    // understands why the toggle is non-interactive.
    Spacer(Modifier.height(8.dp))
    Text(s.settingsStartupLabel, style = AppTypography.label)
    SettingsToggleRow(
        label = s.settingsRunOnStartupLabel,
        // The reason string here is an OS-level diagnostic that's
        // already localised-adjacent (it's in English because it
        // comes from platform APIs), so we leave it as-is on that
        // branch rather than trying to translate it. The default-
        // help branch is a pure UI string and goes through i18n.
        helpText = runOnStartupReason ?: s.settingsRunOnStartupHelp,
        checked = runOnStartup,
        onCheckedChange = onRunOnStartupToggled,
        enabled = runOnStartupReason == null,
    )
}

/**
 * "Restic" section — repo path + optional password file. Keystroke-
 * level persistence so the backup list updates as you type a valid
 * path without needing a Save button.
 */
@Composable
private fun ResticSection(
    resticRepoPath: String,
    resticPasswordFile: String,
    onResticConfigChanged: (String, String) -> Unit,
) {
    val s = LocalStrings.current
    Text(s.settingsResticLabel, style = AppTypography.label)
    Text(s.settingsResticHelp, style = AppTypography.bodySmall.copy(color = AppColors.textDim))

    AppTextField(
        value = resticRepoPath,
        onValueChange = { onResticConfigChanged(it, resticPasswordFile) },
        label = s.settingsResticRepoLabel,
        placeholder = s.settingsResticRepoPlaceholder,
    )

    // Password-file field + file-picker button. We deliberately don't
    // accept the password directly — restic's `--password-file` flag
    // points at a file the user already manages, which keeps the
    // secret out of our pref storage.
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            AppTextField(
                value = resticPasswordFile,
                onValueChange = { onResticConfigChanged(resticRepoPath, it) },
                label = s.settingsResticPasswordFileLabel,
                // OS-specific hint path — a password-file location is
                // a filesystem path, not user-facing prose, so we use
                // the runtime-derived PathHints form instead of the
                // localised placeholder (which hardcoded a
                // slash-style Unix path).
                placeholder = org.open.file.ui.util.PathHints.examplePasswordFile,
            )
        }
        Box(modifier = Modifier.padding(bottom = 4.dp)) {
            ActionButton(
                label = s.settingsResticPasswordFileBrowse,
                icon = Icons.Default.FolderOpen,
                onClick = {
                    val picked = org.open.file.ui.util.pickImageFile(
                        title = s.settingsResticPasswordFileLabel,
                        // Accept any extension — password files are
                        // typically plain text, but users name them
                        // anything (`.txt`, no extension, `.pw`…).
                        extensions = listOf("txt", "pw", "key", "pass"),
                    )
                    if (picked != null) onResticConfigChanged(resticRepoPath, picked)
                },
            )
        }
    }
}

/**
 * "About" section — app identity + one-click reveals for the data
 * folder and error log, plus a link to the homepage.
 *
 * Intentionally read-only: nothing here persists state. It's purely
 * an escape-hatch for users who need to find where OpenFile keeps
 * its files (prefs, schedule JSON, DBs, icon uploads…) or grab the
 * log before filing a bug report.
 *
 * Text is inside a SelectionContainer (ancestor) so the version
 * string and data-folder path can be highlighted and copied.
 */
@Composable
private fun AboutSection() {
    val s = LocalStrings.current
    val dataDir = remember { org.open.file.ui.util.AppInfo.dataDirectory() }
    val logFile = remember { org.open.file.ui.util.AppInfo.errorLogFile() }

    // Identity block — app name + version. Uses the same label /
    // body-small pairing the rest of the dialog uses so it slots in
    // visually.
    Text(s.settingsAboutLabel, style = AppTypography.label)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            // App name + version aren't translated — they're a
            // product identifier, same across locales.
            "${org.open.file.ui.util.AppInfo.DISPLAY_NAME} ${org.open.file.ui.util.AppInfo.VERSION}",
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppColors.textSecondary),
        )
        Text(
            s.settingsAboutTagline,
            style = AppTypography.bodySmall.copy(color = AppColors.textDim),
        )
    }

    Spacer(Modifier.height(8.dp))

    // Data-folder reveal row. We surface the resolved absolute path
    // so users who prefer to paste it into their own shell don't
    // need to click through — selection on the parent container
    // lets them drag to highlight.
    Text(s.settingsDataFolderLabel, style = AppTypography.label)
    Text(
        dataDir.absolutePath,
        style = AppTypography.bodySmall.copy(color = AppColors.textDim),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionButton(
            label = s.settingsShowDataFolder,
            icon = Icons.Default.FolderOpen,
            onClick = {
                // Use openFolder (not reveal) so the file manager
                // opens INSIDE the directory instead of showing it
                // highlighted in its parent — users clicking "Show
                // data folder" want to see prefs/, logs/, etc., not
                // the `.open-file` folder icon sitting in ~/.
                //
                // mkdir first so a fresh install (which hasn't
                // written anything to the data dir yet) still opens
                // cleanly rather than no-op'ing on the
                // nonexistent-target guard.
                if (!dataDir.exists()) dataDir.mkdirs()
                org.open.file.ui.util.openFolderInFileExplorer(dataDir)
            },
        )
    }

    Spacer(Modifier.height(8.dp))

    // Error log reveal. The log might not exist yet on a clean
    // install with no errors — in that case we disable the button
    // and swap the path line for a short helper rather than showing
    // a dead link.
    Text(s.settingsErrorLogLabel, style = AppTypography.label)
    val logExists = logFile.exists()
    Text(
        if (logExists) logFile.absolutePath else s.settingsErrorLogNone,
        style = AppTypography.bodySmall.copy(color = AppColors.textDim),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionButton(
            label = s.settingsOpenErrorLog,
            icon = Icons.Default.Description,
            enabled = logExists,
            onClick = { org.open.file.ui.util.revealInFileExplorer(logFile) },
        )
    }

    Spacer(Modifier.height(8.dp))

    // Homepage / upstream link. Handy enough to keep around even
    // though it's one click away in any browser — users filing a
    // bug often want to check the latest release notes first.
    Text(s.settingsLinksLabel, style = AppTypography.label)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionButton(
            label = s.settingsHomepage,
            icon = Icons.Default.OpenInNew,
            onClick = {
                org.open.file.ui.util.openInBrowser(org.open.file.ui.util.AppInfo.HOMEPAGE_URL)
            },
        )
    }
}

/**
 * Row pairing a primary label + optional help text with a Material3
 * Switch. Standard settings widget — shared by any section that
 * wants a boolean toggle.
 */
@Composable
private fun SettingsToggleRow(
    label: String,
    helpText: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val labelColor = if (enabled) AppColors.textSecondary else AppColors.textDim
    val helpColor = if (enabled) AppColors.textDim else AppColors.textDim.copy(alpha = 0.6f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                label,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = labelColor),
            )
            helpText?.let {
                Text(it, style = AppTypography.bodySmall.copy(color = helpColor))
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppColors.accentLight,
                checkedTrackColor = AppColors.accentBg,
                uncheckedThumbColor = AppColors.textMuted,
                uncheckedTrackColor = AppColors.surface,
            ),
        )
    }
}
