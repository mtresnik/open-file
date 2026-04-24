package org.open.file.ui.state

import androidx.compose.runtime.*

enum class Tab(val label: String) {
    SNAPSHOTS("Snapshots"),
    TEMPLATES("Templates"),
    BACKUPS("Backups"),
}

/**
 * Central app state holder. In a larger app you might use a ViewModel or
 * a DI-provided state store, but for a desktop tool manual wiring is fine.
 */
class AppState {
    var activeTab by mutableStateOf(Tab.SNAPSHOTS)
        private set

    var filterText by mutableStateOf("")
    var showCreateSnapshot by mutableStateOf(false)
    var showCreateTemplate by mutableStateOf(false)

    // Selection — only one thing selected at a time
    var selectedSnapshotId by mutableStateOf<String?>(null)
        private set
    var selectedTemplateId by mutableStateOf<String?>(null)
        private set
    var selectedBackupId by mutableStateOf<String?>(null)
        private set
    /**
     * Source path of the backup *group* the user has selected — i.e. all
     * backups sharing this rootPath. Set when the user clicks a grouped
     * parent row in the backups list; mutually exclusive with
     * [selectedBackupId] so the detail pane knows which view to render
     * (standard single-backup detail vs group history-only).
     */
    var selectedBackupGroupPath by mutableStateOf<String?>(null)
        private set

    /** Mirror of [selectedBackupGroupPath] for the Snapshots tab. */
    var selectedSnapshotGroupPath by mutableStateOf<String?>(null)
        private set

    fun switchTab(tab: Tab) {
        activeTab = tab
        clearSelection()
        filterText = ""
    }

    fun selectSnapshot(id: String?) {
        selectedSnapshotId = id
        selectedTemplateId = null
        selectedBackupId = null
        selectedBackupGroupPath = null
        selectedSnapshotGroupPath = null
    }

    fun selectTemplate(id: String?) {
        selectedTemplateId = id
        selectedSnapshotId = null
        selectedBackupId = null
        selectedBackupGroupPath = null
        selectedSnapshotGroupPath = null
    }

    fun selectBackup(id: String?) {
        selectedBackupId = id
        selectedSnapshotId = null
        selectedTemplateId = null
        selectedBackupGroupPath = null
        selectedSnapshotGroupPath = null
    }

    /** Select a backup group — all backups sharing [path]. Clears single-row selections. */
    fun selectBackupGroup(path: String?) {
        selectedBackupGroupPath = path
        selectedBackupId = null
        selectedSnapshotId = null
        selectedTemplateId = null
        selectedSnapshotGroupPath = null
    }

    /** Select a snapshot group — mirror of [selectBackupGroup]. */
    fun selectSnapshotGroup(path: String?) {
        selectedSnapshotGroupPath = path
        selectedSnapshotId = null
        selectedTemplateId = null
        selectedBackupId = null
        selectedBackupGroupPath = null
    }

    /**
     * Cross-tab navigation: jump to the Snapshots tab and select [snapshotId].
     * Used when the user clicks a snapshot row from inside a backup's detail
     * panel. Clears filter text so the target row is always visible.
     */
    fun navigateToSnapshot(snapshotId: String) {
        activeTab = Tab.SNAPSHOTS
        filterText = ""
        selectSnapshot(snapshotId)
    }

    /**
     * Inverse of [navigateToSnapshot] — jump to the Backups tab and select
     * [backupId]. Used from a snapshot's detail pane to walk up the pin
     * relationship ("this snapshot is used by a backup → take me there").
     */
    fun navigateToBackup(backupId: String) {
        activeTab = Tab.BACKUPS
        filterText = ""
        selectBackup(backupId)
    }

    private fun clearSelection() {
        selectedSnapshotId = null
        selectedTemplateId = null
        selectedBackupId = null
        selectedBackupGroupPath = null
        selectedSnapshotGroupPath = null
    }
}

@Composable
fun rememberAppState(): AppState {
    return remember { AppState() }
}
