package org.open.file.ui.i18n

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

/**
 * Every user-facing string the app renders, bundled into one immutable
 * data class so a locale switch is a single reference swap.
 *
 * This is deliberately code-based (no `.properties` resource bundles):
 *  - Compile-time catches every missing key in a translation — it's just
 *    a data-class constructor with no default values.
 *  - IDE rename / find-usage work across languages out of the box.
 *  - No resource loader, no classloader gotchas on jpackage'd distros.
 *
 * When adding a user-facing string:
 *   1. Add a field here.
 *   2. Add a value in every `*Strings` object under [locales] (or copy
 *      the English one into the untranslated locales as a fallback).
 *   3. Read via `LocalStrings.current.theField` at the call site.
 *
 * Error / debug strings NOT read by users — e.g. stderr messages, log
 * entries — stay in English inline. This class is just for UI text.
 */
@Immutable
data class Strings(
    // Tabs + sidebar
    val tabSnapshots: String,
    val tabTemplates: String,
    val tabBackups: String,
    val sidebarManage: String,
    val sidebarTheme: String,
    val sidebarSettings: String,
    val sidebarLanguage: String,

    // Common action verbs
    val actionCreate: String,
    val actionCancel: String,
    val actionDelete: String,
    val actionRestore: String,
    val actionBrowse: String,
    val actionDone: String,
    val actionSave: String,
    val actionClose: String,
    val actionNew: String,
    /** Inline confirmation that briefly follows a click-to-copy interaction. */
    val actionCopied: String,
    /** Tooltip-ish hint text attached to copyable code chips. */
    val actionCopyHint: String,

    // Snapshots screen
    val snapshotsTitle: String,
    val snapshotsSubtitleFormat: String,  // "%d snapshots · %d unique paths"
    val snapshotsSearchPlaceholder: String,
    val snapshotsEmpty: String,
    val snapshotsCreateTitle: String,
    val snapshotsRootDirectoryLabel: String,
    val snapshotsCreateHelp: String,
    val snapshotsLockedByBackup: String,
    val snapshotsUsedByBackupLabel: String,
    /** Header for the chronological list of every snapshot sharing the same source directory. */
    val snapshotsHistoryHeaderFormat: String,  // "Snapshot history (%d)"
    /** Title in the group-history detail panel header for snapshots. */
    val snapshotsGroupHistoryTitle: String,
    /** Plural count chip on a grouped snapshot parent row. "%d snapshots". */
    val snapshotsGroupCountFormat: String,
    val snapshotsRootPathLabel: String,
    val snapshotsCreatedLabel: String,
    val snapshotsFilesLabel: String,
    val snapshotsDirsLabel: String,
    val snapshotsSizeLabel: String,
    val snapshotsRootHashLabel: String,
    val snapshotsNodeTreeLabel: String,

    // Templates screen
    val templatesTitle: String,
    val templatesSubtitleFormat: String,  // "%d project templates"
    val templatesSearchPlaceholder: String,
    val templatesEmpty: String,
    val templatesCreateTitle: String,
    val templatesNameLabel: String,
    val templatesDescriptionLabel: String,
    val templatesSourceDirectoryLabel: String,
    val templatesSourceHelp: String,
    val templatesIconLabel: String,
    val templatesUploadCustom: String,
    val templatesTagsLabel: String,
    val templatesDescriptionHeader: String,
    val templatesCreatedLabel: String,
    val templatesUpdatedLabel: String,
    val templatesConfigLabel: String,
    val templatesGeneratedStructureLabel: String,
    val templatesOutputDirectoryLabel: String,
    val actionEdit: String,
    val actionGenerate: String,
    val actionScaffold: String,
    /** Badge on packaged (built-in) rows. */
    val templatesBuiltInBadge: String,
    /** Section header in Create dialog + detail pane for inheritance. */
    val templatesExtendsLabel: String,
    /** Placeholder shown in the base-template dropdown when nothing is chosen. */
    val templatesExtendsNone: String,
    /** Section header listing detected installed versions. */
    val templatesToolVersionsLabel: String,
    /** Fallback text when a tool isn't installed on the user's system. */
    val templatesToolNotDetected: String,
    /** Button on each tool row that opens the install page in a browser. */
    val templatesInstallAction: String,
    /** Re-run version detection after installing a new toolchain. */
    val templatesRefreshVersions: String,
    /** Warning shown above Generate when required tools aren't installed. */
    val templatesRequirementsMissing: String,
    /** Source toggle in the Create dialog. */
    val templatesSourceLocal: String,
    val templatesSourceGit: String,
    /** Label + help text for the Git URL input. */
    val templatesGitUrlLabel: String,
    val templatesGitUrlHelp: String,
    /** Output directory label inside the Generate panel. */
    val templatesOutputBrowseTitle: String,
    /** Label for the project-name input in the Generate panel. */
    val templatesProjectNameLabel: String,
    /** Placeholder for the project-name input. */
    val templatesProjectNamePlaceholder: String,
    /** Success toast after a successful scaffold. */
    val templatesScaffoldSuccess: String,

    // Backups screen
    val backupsTitle: String,
    val backupsSubtitleFormat: String,  // "%d backups · %d completed"
    val backupsSearchPlaceholder: String,
    val backupsEmpty: String,
    val backupsCreateTitle: String,
    val backupsSourceLabel: String,
    val backupsCreateHelp: String,
    val backupsOpenInFileExplorer: String,
    val backupsRestoreDialogTitle: String,
    /** One-line summary shown at the top of the restore dialog. */
    val backupsRestoreDialogHelp: String,
    /** Label for the destination text field inside the restore dialog. */
    val backupsRestoreDestinationLabel: String,
    /** Help text beneath the destination field. */
    val backupsRestoreDestinationHelp: String,
    val backupsIncludedSnapshotsFormat: String,  // "Included Snapshots (%d)"
    val backupsStatusLabel: String,
    val backupsDestinationLabel: String,
    val backupsCreatedLabel: String,
    val backupsSizeLabel: String,
    val backupsCompressionLabel: String,
    val backupsStatusCompleted: String,
    val backupsStatusRunning: String,
    val backupsStatusFailed: String,
    /** Header for the chronological list of every backup sharing the same source directory. */
    val backupsHistoryHeaderFormat: String,  // "Backup history (%d)"
    /** One-click shortcut to create another backup with the same rootPath. */
    val backupsNewFromSameSource: String,
    /** Identical-backup confirmation dialog. */
    val dialogIdenticalBackupTitle: String,
    /** Message body, "%s" = name, "%s" = time-ago. */
    val dialogIdenticalBackupMessageFormat: String,
    /** Confirm button on the identical-backup dialog. */
    val dialogIdenticalBackupProceed: String,
    /** Left-rail section labels in the Settings modal. */
    val settingsSectionAppearance: String,
    val settingsSectionConfig: String,
    val settingsSectionRestic: String,
    val settingsSectionAbout: String,
    /** Section header in Settings for backup-related toggles. */
    val settingsBackupsLabel: String,
    /** Toggle label for the identical-backup warning. */
    val settingsWarnIdenticalLabel: String,
    /** Help text beneath the toggle explaining what it does. */
    val settingsWarnIdenticalHelp: String,
    /** Label + help + picker title for the default-target-dir preference. */
    val settingsDefaultTargetDirLabel: String,
    val settingsDefaultTargetDirHelp: String,
    val settingsDefaultTargetDirPickerTitle: String,
    /** Section header + toggle label/help for the Minimize-to-tray preference. */
    val settingsWindowLabel: String,
    val settingsMinimizeToTrayLabel: String,
    val settingsMinimizeToTrayHelp: String,
    /** Section header + toggle label/help for Run-on-startup. */
    val settingsStartupLabel: String,
    val settingsRunOnStartupLabel: String,
    val settingsRunOnStartupHelp: String,
    /** Restic section header in Settings. */
    val settingsResticLabel: String,
    /** Help text for the whole restic section. */
    val settingsResticHelp: String,
    /** Label for the repo-path text field. */
    val settingsResticRepoLabel: String,
    /** Placeholder for the repo-path text field. */
    val settingsResticRepoPlaceholder: String,
    /** Label for the password-file path text field. */
    val settingsResticPasswordFileLabel: String,
    /** Placeholder for the password-file path text field. */
    val settingsResticPasswordFilePlaceholder: String,
    /** Button that opens a file dialog for the password file. */
    val settingsResticPasswordFileBrowse: String,
    /** About-section headers, tagline, and button labels. */
    val settingsAboutLabel: String,
    val settingsAboutTagline: String,
    val settingsDataFolderLabel: String,
    val settingsShowDataFolder: String,
    val settingsErrorLogLabel: String,
    val settingsErrorLogNone: String,
    val settingsOpenErrorLog: String,
    val settingsLinksLabel: String,
    val settingsHomepage: String,
    /** Position marker shown near the header. "Position %d of %d" or similar. */
    val backupsHistoryPositionFormat: String,  // "Position %d of %d"
    /** Label shown on the row representing the currently-selected backup. */
    val backupsHistoryCurrent: String,
    /** Plural count shown on a grouped parent row. "%d backups". */
    val backupsGroupCountFormat: String,
    /** Title in the group-history detail panel header (standalone, no selected backup). */
    val backupsGroupHistoryTitle: String,
    /** "TARGET DIRECTORY" label on the backup detail pane. */
    val backupsTargetDirectoryLabel: String,
    /** Bulk-delete button label + dialog copy shown in the backup parent (group) view. */
    val backupsBulkDeleteButtonFormat: String,       // "Delete all %d backups"
    val backupsBulkDeleteDialogTitle: String,        // "Delete all backups"
    val backupsBulkDeleteHeadlineFormat: String,     // "Delete all %d backups of \"%s\"?"
    val backupsBulkDeleteTooltipFormat: String,      // "Delete all %d backups of %s"
    val backupsBulkDeleteContentDescriptionFormat: String, // accessibility label for the trash icon
    /** Matching strings for the snapshots parent bulk-delete surface — mirrors backups. */
    val snapshotsBulkDeleteButtonFormat: String,     // "Delete all %d snapshots"
    val snapshotsBulkDeleteDialogTitle: String,      // "Delete all snapshots"
    val snapshotsBulkDeleteHeadlineFormat: String,   // "Delete all %d snapshots of \"%s\"?"
    val snapshotsBulkDeleteLockedSomeFormat: String, // "%d pinned by a backup — delete those backups first to remove them."
    val snapshotsBulkDeleteLockedAll: String,        // "Every snapshot in this group is pinned by a backup. Delete the owning backups first to remove them."

    /** Built-in (packaged) template descriptions — one per PackagedTemplate id. */
    val templateDescKotlinGradle: String,
    val templateDescKtorServer: String,
    val templateDescSpringBoot: String,
    val templateDescRustCargo: String,
    val templateDescNodeExpress: String,
    val templateDescReactVite: String,
    val templateDescPythonFastapi: String,
    val templateDescGoModule: String,

    // Dialogs / reusable
    val dialogDeleteTitle: String,
    val dialogDeletePromptFormat: String,  // "Delete %s \"%s\"?"
    val dialogDeleteIrreversible: String,

    // Singular nouns used inside the delete-prompt format. Kept
    // separate from the pluralised tab labels (tabBackups,
    // tabSnapshots, tabTemplates) so the confirmation reads as
    // "Delete backup \"foo\"?" rather than "Delete backups \"foo\"?".
    // Each locale provides its own translation because plural /
    // gender rules differ (e.g. French "sauvegarde" vs "sauvegardes").
    val itemBackup: String,
    val itemSnapshot: String,
    val itemTemplate: String,
    val dialogThemeTitle: String,
    val dialogThemePaletteLabel: String,
    val dialogThemeAccentLabel: String,
    val dialogSettingsTitle: String,
    val dialogLanguageTitle: String,

    // Progress dialog
    val progressCreatingBackup: String,
    val progressScanning: String,
    val progressCompressingFormat: String,  // "Compressing %d of %d files"

    // Validation
    val validationPathNotExist: String,
    val validationPathNotDirectory: String,
    val validationPathNotReadable: String,
    val validationDirectoryReadable: String,
) {
    companion object
}

/** CompositionLocal carrying the active language's [Strings] instance. */
val LocalStrings = compositionLocalOf<Strings> { LocaleEn }
