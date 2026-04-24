package org.open.file.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.open.file.ui.components.*
import org.open.file.ui.data.PACKAGED_ID_PREFIX
import org.open.file.ui.i18n.LocalStrings
import org.open.file.ui.state.AppState
import org.open.file.ui.theme.*
import org.open.file.ui.util.PathValidation
import org.open.file.ui.util.ToolInstaller
import org.open.file.ui.util.guessTemplateIcon
import org.open.file.ui.util.openInBrowser
import org.open.file.ui.util.pickDirectory
import org.open.file.ui.util.pickImageFile
import org.open.file.ui.util.validateDirectory
import java.io.File

// ──────────────────────────────────────────────
// Template data class (UI model — adapt to your domain Template)
// ──────────────────────────────────────────────

data class TemplateUiModel(
    val id: String,
    /** Domain "kind" — currently always "directory". Kept for future template kinds. */
    val type: String,
    val name: String,
    val description: String,
    val tags: List<String>,
    val created: String,
    val updated: String,
    val config: Map<String, String>,
    val previewTree: PreviewNode? = null,
    /**
     * Either a built-in icon key (see [TEMPLATE_ICON_KEYS]) or an absolute
     * path to a user-uploaded image on disk. Defaults to `"generic"` when a
     * template has no preference stored yet.
     */
    val icon: String = "generic",
    /**
     * True for built-in, un-deletable templates that ship with the app.
     * Packaged templates live in code (`PackagedTemplates.kt`), not the
     * SQL store, and the detail pane uses this to hide the Delete button
     * and surface a "Built-in" badge.
     */
    val isPackaged: Boolean = false,
    /**
     * Optional parent template id — the inheritance chain the user asked
     * for. Persisted in [org.open.file.ui.data.TemplateRelations] rather
     * than the SQL store, same reasoning as [icon].
     */
    val baseTemplateId: String? = null,
    /**
     * Tool names this template cares about version selection for.
     * Empty for most user-made templates; packaged ones declare the
     * tools they scaffold against (see `PackagedTemplate.tools`).
     */
    val tools: List<String> = emptyList(),
    /**
     * Subset of [tools] that must be installed before Generate runs.
     * Packaged templates can relax this — e.g. Kotlin + Gradle lists
     * `kotlin` under [tools] for version templating but excludes it
     * from [requiredTools] because Gradle brings its own compiler.
     * Empty for user templates (no gating).
     */
    val requiredTools: List<String> = emptyList(),
    /** Detected installed versions per tool — populated by [org.open.file.ui.util.VersionDetector]. */
    val detectedToolVersions: Map<String, List<String>> = emptyMap(),
    /** Currently-selected version per tool. Defaults to the newest detected, override via the detail pane's dropdown. */
    val selectedToolVersions: Map<String, String> = emptyMap(),
    /**
     * Absolute path to the template's on-disk source directory (the
     * thing that gets copied on Scaffold). Null for packaged templates
     * that generate their starter files programmatically rather than
     * copying an existing tree.
     */
    val sourcePath: String? = null,
)

data class PreviewNode(
    val name: String,
    val type: String, // "file" or "directory"
    val children: List<PreviewNode> = emptyList(),
)

// ──────────────────────────────────────────────
// Templates Screen
// ──────────────────────────────────────────────

@Composable
fun TemplatesScreen(
    state: AppState,
    templates: List<TemplateUiModel>,
    /**
     * Create from a local directory. [sourcePath] is the filesystem
     * path the user picked — passed straight through to the repo.
     */
    onCreateTemplate: (name: String, description: String, rootDir: String, icon: String, tags: List<String>, baseTemplateId: String?) -> Unit,
    /**
     * Create from a git URL. Repo clones into the app's managed
     * template directory on success; error flows through the reporter.
     */
    onCreateTemplateFromGit: (name: String, description: String, gitUrl: String, icon: String, tags: List<String>, baseTemplateId: String?) -> Unit,
    onDeleteTemplate: (templateId: String) -> Unit,
    /** Persist a user-selected tool version against [templateId]. */
    onSelectToolVersion: (templateId: String, tool: String, version: String) -> Unit,
    /** Kick off a scaffold + reveal on success. */
    onScaffoldTemplate: (templateId: String, destinationPath: String, projectName: String) -> Unit,
    /** Drop the version-detection cache and re-read the templates list. */
    onRefreshVersions: () -> Unit,
) {
    // Inline filter so SnapshotStateList mutations flow through without
    // the same stale-remember footgun that bit the other screens.
    val filtered: List<TemplateUiModel> = if (state.filterText.isBlank()) {
        templates
    } else {
        val q = state.filterText.lowercase()
        templates.filter {
            it.name.lowercase().contains(q) ||
                    it.type.contains(q) ||
                    it.tags.any { tag -> tag.contains(q) }
        }
    }

    val selected = filtered.find { it.id == state.selectedTemplateId }
    var showCreate by remember { mutableStateOf(false) }

    val s = LocalStrings.current
    Column(Modifier.fillMaxSize()) {
        HeaderBar(
            title = s.templatesTitle,
            subtitle = s.templatesSubtitleFormat.format(templates.size),
            filterText = state.filterText,
            onFilterChange = { state.filterText = it },
            filterPlaceholder = s.templatesSearchPlaceholder,
            onCreateClick = { showCreate = true },
            createLabel = s.actionNew,
        )

        Row(Modifier.fillMaxSize()) {
            // List
            if (filtered.isEmpty()) {
                EmptyState(s.templatesEmpty)
            } else {
                LazyColumnWithScrollbar(modifier = Modifier.weight(1f)) {
                    items(filtered, key = { it.id }) { tmpl ->
                        ListRow(
                            selected = state.selectedTemplateId == tmpl.id,
                            onClick = { state.selectTemplate(tmpl.id) },
                            icon = { TemplateIcon(iconRef = tmpl.icon, size = 36.dp) },
                            content = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        tmpl.name,
                                        style = AppTypography.rowTitle,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    // "Built-in" pill so users see at
                                    // a glance which rows are packaged
                                    // templates they can't delete.
                                    if (tmpl.isPackaged) {
                                        SingleLineText(
                                            s.templatesBuiltInBadge,
                                            style = AppTypography.chip.copy(color = AppColors.accentLight),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(AppColors.accentBg)
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    // Packaged templates carry their
                                    // description via i18n keys —
                                    // fall back to the UI model's
                                    // English string (which is also
                                    // used by non-packaged / user
                                    // templates).
                                    org.open.file.ui.data.localizedPackagedTemplateDescription(tmpl.id, s)
                                        ?: tmpl.description,
                                    style = AppTypography.bodySmall,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    tmpl.tags.take(4).forEach { tag -> TagChip(tag) }
                                }
                            },
                            trailing = {
                                // Packaged rows don't track "updated"
                                // — they're immutable. Show the tool
                                // count as a lightweight substitute.
                                if (tmpl.isPackaged) {
                                    if (tmpl.tools.isNotEmpty()) {
                                        SingleLineText(
                                            "${tmpl.tools.size} tools",
                                            style = AppTypography.codeSmall,
                                        )
                                    }
                                } else {
                                    SingleLineText("updated ${tmpl.updated}", style = AppTypography.codeSmall)
                                }
                            },
                            // Trash icon only for user-owned rows.
                            // Packaged templates ship with the app
                            // and can't be removed — omitting the
                            // action entirely is cleaner than
                            // showing a permanently-disabled icon.
                            actions = if (tmpl.isPackaged) null else {
                                {
                                    RowDeleteIcon(
                                        itemLabel = "template",
                                        itemName = tmpl.name,
                                        onConfirm = { onDeleteTemplate(tmpl.id) },
                                    )
                                }
                            },
                        )
                    }
                }
            }

            // Detail
            if (selected != null) {
                TemplateDetailPanel(
                    selected,
                    allTemplates = templates,
                    onClose = { state.selectTemplate(null) },
                    onDelete = { onDeleteTemplate(selected.id) },
                    onNavigateToTemplate = { id -> state.selectTemplate(id) },
                    onSelectToolVersion = { tool, version ->
                        onSelectToolVersion(selected.id, tool, version)
                    },
                    onScaffold = { dest, name -> onScaffoldTemplate(selected.id, dest, name) },
                    onRefreshVersions = onRefreshVersions,
                )
            }
        }
    }

    // Create Template dialog
    CreateTemplateDialog(
        visible = showCreate,
        onDismiss = { showCreate = false },
        availableTemplates = templates,
        onCreateLocal = { name, desc, rootDir, icon, tags, baseId ->
            onCreateTemplate(name, desc, rootDir, icon, tags, baseId)
            showCreate = false
        },
        onCreateGit = { name, desc, gitUrl, icon, tags, baseId ->
            onCreateTemplateFromGit(name, desc, gitUrl, icon, tags, baseId)
            showCreate = false
        },
    )
}

// ──────────────────────────────────────────────
// Template Detail Panel
// ──────────────────────────────────────────────

@Composable
private fun TemplateDetailPanel(
    template: TemplateUiModel,
    allTemplates: List<TemplateUiModel>,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onNavigateToTemplate: (templateId: String) -> Unit,
    onSelectToolVersion: (tool: String, version: String) -> Unit,
    /**
     * Materialise [template] into [destinationPath] under the given
     * [projectName]. Main.kt runs the actual scaffold on its IO
     * dispatcher via the repository and surfaces success / failure
     * via the toast reporter.
     */
    onScaffold: (destinationPath: String, projectName: String) -> Unit,
    /** Clear the detector cache and refresh the template list. */
    onRefreshVersions: () -> Unit,
) {
    // Used below to colour the Scaffold button — keyed on the icon label so
    // the accent colour matches the chosen stack. Custom-uploaded icons fall
    // through to the generic palette.
    val accent = techColorFor(if (template.icon in TEMPLATE_ICON_KEYS) template.icon else "generic")
    // Output directory — prepopulated from the most recent scaffold so
    // users who keep all their projects in one place don't retype the
    // path every time. Not keyed on template.id so switching between
    // templates preserves the value within a session; persisted via
    // TemplatePreferences so it survives restarts. Blank on first
    // run — the placeholder takes over.
    val templatePrefs = remember { org.open.file.ui.data.TemplatePreferences() }
    var genPath by remember { mutableStateOf(templatePrefs.loadLastOutputDir().orEmpty()) }
    // Project name is required for scaffold — baked into generated
    // config files (build.gradle.kts rootProject.name, Cargo.toml
    // package name, go.mod module path). Keyed on template.id so
    // switching templates clears the name; users rarely want to
    // reuse a name across two different stacks.
    var genProjectName by remember(template.id) { mutableStateOf("") }
    // Confirm-before-delete gate — same pattern as the other detail panels.
    var confirmDelete by remember(template.id) { mutableStateOf(false) }

    // Resolve the parent template for the inheritance chip. Works for
    // both packaged parent ids ("packaged:...") and user UUID ids since
    // the list is unified.
    val baseTemplate = template.baseTemplateId?.let { bid -> allTemplates.firstOrNull { it.id == bid } }

    DetailPanel(
        onClose = onClose,
        header = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TemplateIcon(iconRef = template.icon, size = 36.dp)
                Column {
                    Text(template.name, style = AppTypography.pageTitle.copy(fontSize = 15.sp))
                    Text(templateIconLabel(template.icon), style = AppTypography.bodySmall)
                }
            }
        }
    ) {
        val sDetail = LocalStrings.current

        // "Built-in" badge on packaged templates — matches the list
        // row's badge so the state reads consistently across surfaces.
        if (template.isPackaged) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Default.Verified,
                    contentDescription = null,
                    tint = AppColors.accentLight,
                    modifier = Modifier.size(14.dp),
                )
                SingleLineText(
                    sDetail.templatesBuiltInBadge,
                    style = AppTypography.chip.copy(color = AppColors.accentLight),
                )
            }
        }

        DetailField(sDetail.templatesDescriptionHeader) {
            // Same i18n fallback as the list row — packaged
            // templates resolve through the locale bundle while user
            // templates keep their free-text description.
            Text(
                org.open.file.ui.data.localizedPackagedTemplateDescription(template.id, sDetail)
                    ?: template.description,
                style = AppTypography.body.copy(color = AppColors.textMuted, lineHeight = 20.sp),
            )
        }

        // Inheritance — clickable parent chip when a base template is set.
        if (baseTemplate != null) {
            DetailField(sDetail.templatesExtendsLabel) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppColors.accentBg)
                        .clickable { onNavigateToTemplate(baseTemplate.id) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    TemplateIcon(iconRef = baseTemplate.icon, size = 22.dp)
                    Text(
                        baseTemplate.name,
                        style = AppTypography.body.copy(fontSize = 12.5.sp, color = AppColors.accentLight),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = AppColors.accentLight,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        // Detected tool versions — one dropdown per tool, each a
        // combobox of everything VersionDetector found. Changes persist
        // via [onSelectToolVersion] so the user's choice sticks across
        // sessions. Each row also carries an Install button that opens
        // the tool's official install page in the user's browser.
        if (template.tools.isNotEmpty()) {
            // Custom field header with a Refresh affordance — lets
            // users re-detect after installing a new toolchain without
            // restarting the whole app (which otherwise holds onto
            // both the stale cache AND the old PATH from launch).
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        sDetail.templatesToolVersionsLabel.uppercase(),
                        style = AppTypography.label,
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onRefreshVersions)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = AppColors.accentLight,
                            modifier = Modifier.size(12.dp),
                        )
                        SingleLineText(
                            sDetail.templatesRefreshVersions,
                            style = AppTypography.chip.copy(color = AppColors.accentLight),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    template.tools.forEach { tool ->
                        val installUrl = ToolInstaller.installUrlFor(tool)
                        ToolVersionRow(
                            tool = tool,
                            detected = template.detectedToolVersions[tool].orEmpty(),
                            selected = template.selectedToolVersions[tool].orEmpty(),
                            notDetectedLabel = sDetail.templatesToolNotDetected,
                            installLabel = sDetail.templatesInstallAction,
                            installUrl = installUrl,
                            onSelect = { version -> onSelectToolVersion(tool, version) },
                            onInstall = { installUrl?.let { openInBrowser(it) } },
                            // Any tool not on requiredTools renders as
                            // Optional — mostly `kotlin` on Gradle-
                            // backed templates (Gradle fetches its own
                            // compiler), but the pattern scales to any
                            // future template that carries templated-
                            // only toolchain metadata.
                            isRequired = tool in template.requiredTools,
                        )
                    }
                }
            }
        }

        DetailField(sDetail.templatesTagsLabel) {
            TagRow(template.tags)
        }

        // Created / Updated row. Packaged templates have a synthetic
        // `created = "Built-in"` string that duplicates the "Built-in"
        // pill already rendered next to the title — suppress the
        // whole row on packaged templates so the user doesn't see the
        // same label twice. User-owned templates keep both columns
        // (created + updated dates are different and both useful).
        if (!template.isPackaged) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                DetailFieldText(sDetail.templatesCreatedLabel, template.created)
                DetailFieldText(sDetail.templatesUpdatedLabel, template.updated)
            }
        }

        // Config section is templated-only metadata (nothing populates
        // it from the domain layer today). When the map is empty we
        // don't render the field at all — an empty box between Tags
        // and Tool Versions was noise, not signal.
        if (template.config.isNotEmpty()) {
            DetailField(sDetail.templatesConfigLabel) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.02f))
                        .padding(12.dp)
                ) {
                    template.config.forEach { (key, value) ->
                        Row {
                            Text("$key: ", style = AppTypography.codeSmall.copy(color = AppColors.textDim))
                            Text(value, style = AppTypography.codeSmall.copy(color = AppColors.textMuted))
                        }
                    }
                }
            }
        }

        template.previewTree?.let { tree ->
            DetailField(sDetail.templatesGeneratedStructureLabel) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.015f))
                        .padding(vertical = 8.dp)
                        .heightIn(max = 260.dp)
                ) {
                    PreviewTreeView(tree)
                }
            }
        }

        // Requirements gate: only the *required* tools block
        // scaffolding. `tools` can include toolchains we merely
        // template into the scaffold (e.g. kotlin version in
        // build.gradle.kts) — those don't need to be on PATH for
        // the build to work, so they're not gating requirements.
        val missingTools = template.requiredTools.filter {
            template.detectedToolVersions[it].isNullOrEmpty()
        }
        val requirementsMet = missingTools.isEmpty()

        // Scaffolding block is now conditional on requirements — no
        // Generate button gating the UI, users just see the form
        // inline when the template is ready to run. Missing tools
        // surface as a warning banner in place of the form so the
        // user still gets feedback about what to install.
        if (!requirementsMet) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppColors.warning.copy(alpha = 0.10f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = AppColors.warning,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    // "{warning} Missing: kotlin, gradle" — concise
                    // enough to fit in the narrow detail pane.
                    "${sDetail.templatesRequirementsMissing} (${missingTools.joinToString(", ")})",
                    style = AppTypography.bodySmall.copy(color = AppColors.warning),
                )
            }
        } else {
            // Inline scaffolding form — shown only when the
            // template's required tools are all installed.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.02f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Output directory with inline Browse — same affordance
                // pattern as the Source Directory picker in the Create
                // dialog. Prepopulated from TemplatePreferences so
                // users who kept projects in the same parent dir
                // don't retype on every scaffold.
                PathInputField(
                    value = genPath,
                    onValueChange = { genPath = it },
                    label = sDetail.templatesOutputDirectoryLabel,
                    placeholder = org.open.file.ui.util.PathHints.documentsDir,
                    helpText = null,
                    onBrowse = {
                        pickDirectory(
                            title = sDetail.templatesOutputBrowseTitle,
                            startDirectory = genPath.ifBlank { null },
                        )?.let { chosen -> genPath = chosen }
                    },
                )

                // Project name + Scaffold sit in one row, same
                // baseline alignment as the Browse button in the
                // source directory field above. Both fields are
                // required — Scaffold's `enabled` is gated on
                // non-blank output path + non-blank project name.
                val trimmedName = genProjectName.trim()
                val canScaffold = genPath.isNotBlank() && trimmedName.isNotBlank()
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        AppTextField(
                            value = genProjectName,
                            onValueChange = { genProjectName = it },
                            label = sDetail.templatesProjectNameLabel,
                            placeholder = sDetail.templatesProjectNamePlaceholder,
                            mono = false,
                        )
                    }
                    // Nudge the button down so its baseline aligns
                    // with the text field's input row rather than the
                    // uppercase label above it — same trick
                    // PathInputField uses for Browse.
                    Box(modifier = Modifier.padding(bottom = 4.dp)) {
                        ActionButton(
                            sDetail.actionScaffold, Icons.Default.CreateNewFolder,
                            enabled = canScaffold,
                            onClick = {
                                if (canScaffold) {
                                    val trimmedPath = genPath.trim()
                                    // Remember the parent dir for next
                                    // time — saves the user retyping
                                    // /Users/alice/Projects across
                                    // every scaffold.
                                    templatePrefs.setLastOutputDir(trimmedPath)
                                    onScaffold(trimmedPath, trimmedName)
                                }
                            },
                            backgroundColor = accent.bg,
                            borderColor = accent.accent.copy(alpha = 0.2f),
                            color = accent.fg,
                        )
                    }
                }
            }
        }

        // Destructive action for user-owned templates — on its own
        // row so a narrow detail pane can't crush the label. Edit
        // was previously alongside here but is dropped until the
        // TODO ("real edit flow") lands; trash icons in the list
        // row + this button cover the lifecycle verbs users
        // currently need.
        if (!template.isPackaged) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DangerButton(sDetail.actionDelete, Icons.Default.Delete, onClick = { confirmDelete = true })
            }
        }
    }

    ConfirmDeleteDialog(
        visible = confirmDelete,
        itemLabel = LocalStrings.current.itemTemplate,
        itemName = template.name,
        onConfirm = onDelete,
        onDismiss = { confirmDelete = false },
    )
}

// ──────────────────────────────────────────────
// Preview Tree (for templates — simpler than SnapshotNode tree)
// ──────────────────────────────────────────────

@Composable
private fun PreviewTreeView(node: PreviewNode, depth: Int = 0) {
    var expanded by remember { mutableStateOf(depth < 3) }
    val isDir = node.type == "directory"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isDir) Modifier.clickable { expanded = !expanded } else Modifier)
            .padding(start = (10 + depth * 16).dp, top = 3.dp, bottom = 3.dp, end = 8.dp)
    ) {
        if (isDir) {
            Icon(
                if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = AppColors.accentLight,
                modifier = Modifier.size(12.dp)
            )
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            if (isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = if (isDir) AppColors.accentLight else AppColors.textDim,
            modifier = Modifier.size(if (isDir) 15.dp else 14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            node.name,
            style = TextStyle(fontSize = 12.5.sp, fontFamily = FontFamily.Monospace, color = if (isDir) AppColors.accentLight else AppColors.textMuted)
        )
    }

    if (isDir && expanded) {
        node.children.forEach { child -> PreviewTreeView(child, depth + 1) }
    }
}

// ──────────────────────────────────────────────
// Create Template Dialog
// ──────────────────────────────────────────────

/**
 * Source-of-template choice in the Create dialog — local filesystem
 * path vs remote git URL. Kept as a tiny private enum so the dialog's
 * `when(sourceMode)` branches read cleanly.
 */
private enum class TemplateSourceMode { LOCAL, GIT }

@Composable
private fun CreateTemplateDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    /** The full list of existing templates — used as options for the "Extends" picker. */
    availableTemplates: List<TemplateUiModel>,
    onCreateLocal: (name: String, description: String, rootDir: String, icon: String, tags: List<String>, baseTemplateId: String?) -> Unit,
    onCreateGit: (name: String, description: String, gitUrl: String, icon: String, tags: List<String>, baseTemplateId: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var rootDir by remember { mutableStateOf("") }
    var gitUrl by remember { mutableStateOf("") }
    var sourceMode by remember { mutableStateOf(TemplateSourceMode.LOCAL) }
    var icon by remember { mutableStateOf("generic") }
    // Tracks whether the user has explicitly picked an icon. Until they
    // do, we keep updating it from the auto-guess every time the name
    // / description changes. First manual pick flips this and pins the
    // choice so future typing doesn't stomp on it.
    var iconOverridden by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf(listOf<String>()) }
    var baseTemplateId by remember { mutableStateOf<String?>(null) }

    // Auto-guess icon from name + description as the user types.
    // Re-derives on every change, stops affecting state once the user
    // explicitly picks one from the dropdown.
    val guessedIcon = remember(name, desc) { guessTemplateIcon(name, desc) }
    LaunchedEffect(guessedIcon, iconOverridden) {
        if (!iconOverridden) icon = guessedIcon
    }

    val s = LocalStrings.current
    // Validate the source directory only when in Local mode — for Git
    // we can't validate until we clone, so we just require a non-empty
    // URL on submit.
    val validation = remember(rootDir) { validateDirectory(rootDir) }
    val canCreate = when (sourceMode) {
        TemplateSourceMode.LOCAL -> name.isNotBlank() && validation is PathValidation.Valid
        TemplateSourceMode.GIT -> name.isNotBlank() && gitUrl.isNotBlank()
    }

    fun reset() {
        name = ""; desc = ""; rootDir = ""; gitUrl = ""; icon = "generic"
        iconOverridden = false; tags = emptyList(); baseTemplateId = null
        sourceMode = TemplateSourceMode.LOCAL
    }

    AppDialog(visible = visible, onDismiss = { reset(); onDismiss() }, title = s.templatesCreateTitle) {
        AppTextField(value = name, onValueChange = { name = it }, label = s.templatesNameLabel, placeholder = "My Awesome Template", mono = false)
        AppTextField(value = desc, onValueChange = { desc = it }, label = s.templatesDescriptionLabel, placeholder = "What does this template scaffold?", mono = false)

        // Source mode toggle — segmented-control feel. Active mode is
        // backed with the accent tint so it stands out against the
        // dormant sibling.
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SourceModeButton(
                label = s.templatesSourceLocal,
                icon = Icons.Default.Folder,
                active = sourceMode == TemplateSourceMode.LOCAL,
                onClick = { sourceMode = TemplateSourceMode.LOCAL },
                modifier = Modifier.weight(1f),
            )
            SourceModeButton(
                label = s.templatesSourceGit,
                icon = Icons.Default.Cloud,
                active = sourceMode == TemplateSourceMode.GIT,
                onClick = { sourceMode = TemplateSourceMode.GIT },
                modifier = Modifier.weight(1f),
            )
        }

        when (sourceMode) {
            TemplateSourceMode.LOCAL -> {
                PathInputField(
                    value = rootDir,
                    onValueChange = { rootDir = it },
                    label = s.templatesSourceDirectoryLabel,
                    // OS-specific hint — resolves to something like
                    // `C:\Users\alice\Documents\my-project` on Windows
                    // or `/home/alice/Documents/my-project` on Linux
                    // rather than a hardcoded `/home/user/…`.
                    placeholder = org.open.file.ui.util.PathHints.exampleProjectPath,
                    helpText = s.templatesSourceHelp,
                    onBrowse = {
                        pickDirectory(
                            title = s.templatesSourceDirectoryLabel,
                            startDirectory = rootDir.ifBlank { null },
                        )?.let { chosen -> rootDir = chosen }
                    },
                )
                PathStatusLine(validation)
            }
            TemplateSourceMode.GIT -> {
                AppTextField(
                    value = gitUrl,
                    onValueChange = { gitUrl = it },
                    label = s.templatesGitUrlLabel,
                    placeholder = "https://github.com/user/repo.git",
                    helpText = s.templatesGitUrlHelp,
                )
            }
        }

        // "Extends" picker — optional parent template. Lists packaged
        // templates first (they're the most common extension target),
        // then user templates, then a "none" entry to clear the choice.
        BaseTemplatePicker(
            selected = baseTemplateId,
            options = availableTemplates,
            onSelect = { baseTemplateId = it },
        )

        IconDropdown(selected = icon, onSelect = { icon = it; iconOverridden = true })

        DetailField(s.templatesTagsLabel) {
            TagInput(tags = tags, onAddTag = { tags = tags + it }, onRemoveTag = { t -> tags = tags.filter { it != t } })
        }

        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            ActionButton(s.actionCancel, Icons.Default.Close, onClick = { reset(); onDismiss() })
            Spacer(Modifier.width(8.dp))
            ActionButton(
                s.templatesCreateTitle, Icons.Default.Add,
                enabled = canCreate,
                onClick = {
                    if (!canCreate) return@ActionButton
                    when (sourceMode) {
                        TemplateSourceMode.LOCAL ->
                            onCreateLocal(name.trim(), desc.trim(), rootDir.trim(), icon, tags, baseTemplateId)
                        TemplateSourceMode.GIT ->
                            onCreateGit(name.trim(), desc.trim(), gitUrl.trim(), icon, tags, baseTemplateId)
                    }
                    reset()
                },
                backgroundColor = AppColors.accentBg,
                borderColor = AppColors.accentBorder,
                color = AppColors.accentLight,
            )
        }
    }
}

/** Toggle tile for the source-mode segmented control. Accent-tinted when active. */
@Composable
private fun SourceModeButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) AppColors.accentBg else Color.White.copy(alpha = 0.03f))
            .border(
                width = 1.dp,
                color = if (active) AppColors.accentBorder else AppColors.borderLight,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) AppColors.accentLight else AppColors.textMuted,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = TextStyle(
                fontSize = 12.5.sp,
                fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal,
                color = if (active) AppColors.accentLight else AppColors.textMuted,
            ),
        )
    }
}

// ──────────────────────────────────────────────
// Path Input + Browse (compound field)
// ──────────────────────────────────────────────

/**
 * Text input with an inline "Browse…" button to its right. Used in the create
 * template dialog so the directory picker trigger sits next to the field it
 * fills, rather than being dropped on its own row underneath.
 */
@Composable
private fun PathInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    helpText: String?,
    onBrowse: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            AppTextField(
                value = value,
                onValueChange = onValueChange,
                label = label,
                placeholder = placeholder,
                helpText = helpText,
            )
        }
        // Nudge the button down so its baseline lines up with the text
        // field's input row rather than the label above it.
        Box(modifier = Modifier.padding(bottom = if (helpText != null) 20.dp else 4.dp)) {
            ActionButton("Browse…", Icons.Default.FolderOpen, onClick = onBrowse)
        }
    }
}

// ──────────────────────────────────────────────
// Icon Dropdown (built-in + custom upload)
// ──────────────────────────────────────────────

/**
 * Dropdown picker for template icons. Selecting a built-in entry stores the
 * key ("kotlin", "react", …); choosing "Upload custom image…" opens a native
 * file picker restricted to PNG/ICO/JPG and stores the file's absolute path,
 * which the caller is expected to round-trip through [IconPreferences] to copy
 * into the UI's managed icon directory.
 */
@Composable
private fun IconDropdown(
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("ICON", style = AppTypography.label)

        Box {
            // Anchor row — renders like AppTextField so the dropdown feels
            // like any other form control.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .clickable { expanded = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                TemplateIcon(iconRef = selected, size = 22.dp)
                Text(
                    templateIconLabel(selected),
                    style = TextStyle(fontSize = 13.sp, color = AppColors.textSecondary),
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = AppColors.textDim,
                    modifier = Modifier.size(16.dp),
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                TEMPLATE_ICON_KEYS.forEach { key ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TemplateIcon(iconRef = key, size = 20.dp)
                                Text(
                                    templateIconLabel(key),
                                    style = TextStyle(fontSize = 13.sp, color = AppColors.textSecondary),
                                )
                            }
                        },
                        onClick = {
                            onSelect(key)
                            expanded = false
                        },
                    )
                }

                // Custom upload: launches a native file picker restricted to
                // image extensions. The returned path is stored as-is; the
                // repository layer copies it into the UI's managed icons dir.
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.UploadFile,
                                contentDescription = null,
                                tint = AppColors.accentLight,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                "Upload custom image…",
                                style = TextStyle(fontSize = 13.sp, color = AppColors.accentLight),
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        pickImageFile(title = "Choose template icon")?.let { path ->
                            onSelect(path)
                        }
                    },
                )
            }
        }
    }
}

// ──────────────────────────────────────────────
// Template Icon rendering
// ──────────────────────────────────────────────

/**
 * Render a template's icon given an [iconRef] that is either a built-in key
 * (rendered as the two-letter initial on the tech-coloured chip) or an
 * absolute path to a user-supplied image file.
 *
 * Uploaded images are loaded with [loadImageBitmap], cached across recompositions
 * keyed on the path, and gracefully fall back to the generic-key initials if
 * decoding fails (e.g. an unsupported format like a raw .ico — ImageIO doesn't
 * ship with .ico support by default).
 */
@Composable
internal fun TemplateIcon(iconRef: String, size: androidx.compose.ui.unit.Dp) {
    val isPath = iconRef.contains(File.separatorChar) || iconRef.startsWith("/") || iconRef.contains(":\\")
    val tc = techColorFor(if (!isPath && iconRef in TEMPLATE_ICON_KEYS) iconRef else "generic")

    if (isPath) {
        val bitmap: ImageBitmap? = remember(iconRef) {
            runCatching {
                val f = File(iconRef)
                if (!f.exists() || !f.canRead()) null
                else f.inputStream().use { loadImageBitmap(it) }
            }.getOrNull()
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(tc.bg),
        ) {
            if (bitmap != null) {
                Image(
                    painter = BitmapPainter(bitmap),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(size),
                )
            } else {
                // Decoding failed (missing file, unsupported format). Fall
                // through to the filename's first two chars so the user still
                // sees something recognisable.
                Text(
                    File(iconRef).nameWithoutExtension.take(2).uppercase(),
                    style = TextStyle(
                        fontSize = (size.value * 0.3f).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = tc.fg,
                    ),
                )
            }
        }
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(tc.bg),
        ) {
            Text(
                iconRef.take(2).uppercase(),
                style = TextStyle(
                    fontSize = (size.value * 0.3f).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = tc.fg,
                ),
            )
        }
    }
}

/** Human-readable label for an [iconRef] — built-in keys pass through, file paths show the filename. */
private fun templateIconLabel(iconRef: String): String {
    val isPath = iconRef.contains(File.separatorChar) || iconRef.startsWith("/") || iconRef.contains(":\\")
    return if (isPath) File(iconRef).name else iconRef
}

// Needed for the Color.White reference used inline
private val Color.Companion.White get() = androidx.compose.ui.graphics.Color.White

// ──────────────────────────────────────────────
// Base-template picker (Create dialog + detail pane)
// ──────────────────────────────────────────────

/**
 * Dropdown of every template the user could extend from, plus a "None"
 * entry to clear the selection. Packaged templates are listed first
 * (they're the most common extension target and users should see them
 * promoted), then user templates.
 *
 * Matches [IconDropdown]'s visual pattern so Create-dialog form controls
 * feel uniform: anchor row that looks like a text field, Material3
 * `DropdownMenu` for the options.
 */
@Composable
private fun BaseTemplatePicker(
    selected: String?,
    options: List<TemplateUiModel>,
    onSelect: (String?) -> Unit,
) {
    val s = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = options.firstOrNull { it.id == selected }

    // Packaged first, then user, keyed by id so swapping order doesn't
    // blow composition identity on the DropdownMenuItem keys.
    val ordered = remember(options) {
        options.partition { it.isPackaged }.let { (packaged, user) -> packaged + user }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(s.templatesExtendsLabel.uppercase(), style = AppTypography.label)
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .clickable { expanded = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                if (selectedOption != null) {
                    TemplateIcon(iconRef = selectedOption.icon, size = 22.dp)
                    Text(
                        selectedOption.name,
                        style = TextStyle(fontSize = 13.sp, color = AppColors.textSecondary),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        s.templatesExtendsNone,
                        style = TextStyle(fontSize = 13.sp, color = AppColors.textDim),
                        modifier = Modifier.weight(1f),
                    )
                }
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = AppColors.textDim,
                    modifier = Modifier.size(16.dp),
                )
            }
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(s.templatesExtendsNone, style = TextStyle(fontSize = 13.sp, color = AppColors.textDim)) },
                    onClick = { onSelect(null); expanded = false },
                )
                ordered.forEach { tmpl ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TemplateIcon(iconRef = tmpl.icon, size = 20.dp)
                                Text(
                                    tmpl.name,
                                    style = TextStyle(fontSize = 13.sp, color = AppColors.textSecondary),
                                )
                                if (tmpl.isPackaged) {
                                    Text(
                                        s.templatesBuiltInBadge,
                                        style = AppTypography.chip.copy(color = AppColors.accentLight),
                                    )
                                }
                            }
                        },
                        onClick = { onSelect(tmpl.id); expanded = false },
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// Tool version row (detail pane)
// ──────────────────────────────────────────────

/**
 * One row for one tool in the detail pane's "Detected versions"
 * section. Renders the tool name + a dropdown of installed versions;
 * picking a new value persists via [onSelect] so the choice outlives
 * the composition.
 *
 * If nothing was detected, the dropdown is replaced with a quiet
 * "Not installed" label so the user knows the status at a glance.
 */
@Composable
private fun ToolVersionRow(
    tool: String,
    detected: List<String>,
    selected: String,
    notDetectedLabel: String,
    installLabel: String,
    installUrl: String?,
    onSelect: (String) -> Unit,
    onInstall: () -> Unit,
    /**
     * False when the tool is referenced by the scaffold but isn't on
     * the template's `requiredTools` list (e.g. `kotlin` on the Kotlin
     * + Gradle template — Gradle brings its own compiler, so having
     * `kotlinc` on PATH is nice-to-have, not required). Optional rows
     * render an "Optional" pill and suppress the orange "Not detected"
     * warning so the template doesn't look broken when the tool is
     * missing.
     */
    isRequired: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.02f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        // Tool name + optional "Optional" pill stacked so wide labels
        // ("Gradle") and the pill don't push the version column off.
        Column(
            modifier = Modifier.width(90.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                tool.replaceFirstChar { it.uppercase() },
                style = AppTypography.body.copy(fontSize = 12.5.sp),
            )
            if (!isRequired) {
                Text(
                    "Optional",
                    style = AppTypography.chip.copy(color = AppColors.textDim),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        if (detected.isEmpty()) {
            // Required → orange warning ("Not detected"). Optional →
            // dim muted label so the user sees the tool exists but
            // understands missing it is fine.
            Text(
                if (isRequired) notDetectedLabel else "Not installed (not required)",
                style = if (isRequired) {
                    AppTypography.bodySmall.copy(color = AppColors.warning)
                } else {
                    AppTypography.bodySmall.copy(color = AppColors.textDim)
                },
                modifier = Modifier.weight(1f),
            )
        } else {
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppColors.accent.copy(alpha = 0.08f))
                        .clickable { expanded = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        selected.ifBlank { detected.first() },
                        style = AppTypography.code,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = AppColors.textDim,
                        modifier = Modifier.size(14.dp),
                    )
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    detected.forEach { version ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(version, style = AppTypography.code) },
                            onClick = { onSelect(version); expanded = false },
                        )
                    }
                }
            }
        }
        // Install button — always shown when we know a URL. For
        // "not installed" rows it's the user's next step; for
        // already-installed rows it's how they get a newer version.
        if (installUrl != null) {
            ActionButton(
                label = installLabel,
                icon = Icons.Default.OpenInNew,
                onClick = onInstall,
            )
        }
    }
}

// Swallow unused import warning — this symbol is referenced from comments
// / docstrings that describe the packaged-template id scheme.
@Suppress("unused")
private val packagedIdPrefixRef = PACKAGED_ID_PREFIX
