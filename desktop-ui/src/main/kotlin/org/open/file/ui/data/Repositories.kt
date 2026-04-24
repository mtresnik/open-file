package org.open.file.ui.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import org.open.file.archive.ArchiveExtractor
import org.open.file.archive.ArchiveFormat
import org.open.file.backup.BackupService
import org.open.file.backup.gen.BackupArchiver
import org.open.file.backup.models.Backup
import org.open.file.backup.models.CompressionType
import org.open.file.backup.models.SavedBackup
import org.open.file.snapshot.NodeService
import org.open.file.snapshot.SnapshotService
import org.open.file.snapshot.gen.TreeBuilder
import org.open.file.snapshot.models.SavedSnapshot
import org.open.file.snapshot.store.domain.SnapshotNode
import org.open.file.snapshot.store.domain.toDomain
import org.open.file.template.TemplateService
import org.open.file.template.models.Template
import org.open.file.template.models.directory.DirectoryTemplate
import org.open.file.template.models.directory.DirectoryTemplateConfig
import org.open.file.ui.screens.BackupStatus
import org.open.file.ui.screens.BackupUiModel
import org.open.file.ui.screens.TemplateUiModel
import org.open.file.ui.util.GitCloner
import org.open.file.utils.FileSystemUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import kotlin.time.Clock

// ──────────────────────────────────────────────
// Repositories
//
// These classes are the boundary between UI-land (Compose state lists,
// EDT-only rendering) and service-land (blocking DAO calls into SQLite /
// Mongo). Every public method is a suspend function that bounces through an
// injectable [CoroutineDispatcher] (default: [Dispatchers.IO]) so the Compose
// event thread never waits on JDBC, Mongo, or filesystem work.
//
// Callers launch these from a scope tied to the UI lifecycle — typically
// `rememberCoroutineScope()` — and update their state lists with the result
// when the suspend function returns. Because a `withContext` coroutine
// resumes on its caller's dispatcher, those state updates naturally run on
// the AWT EDT, which is what Compose expects.
// ──────────────────────────────────────────────

/**
 * Snapshot CRUD for the desktop UI.
 *
 * Wraps [SnapshotService] + [NodeService] and hides the tree-building step
 * that happens on creation. Services are built lazily so classpath
 * misconfiguration (missing DAO impl on the ServiceLoader path) surfaces on
 * first use — where it can be caught by the caller — rather than at
 * construction time.
 */
class SnapshotRepository(
    snapshotService: SnapshotService? = null,
    nodeService: NodeService? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val snapshotService: SnapshotService by lazy { snapshotService ?: SnapshotService() }
    private val nodeService: NodeService by lazy { nodeService ?: NodeService() }

    /** Fetch every persisted snapshot, normalised to the UI-facing [SavedSnapshot]. */
    suspend fun listAll(): List<SavedSnapshot> = withContext(dispatcher) {
        snapshotService.getAll().map { it.toSaved() }
    }

    /** Load one snapshot's node tree, or null if it's not in the store. */
    suspend fun getTree(snapshotId: String): SnapshotNode? = withContext(dispatcher) {
        nodeService[snapshotId]
    }

    /** Batch-load trees so the initial screen render has stats for every snapshot. */
    suspend fun listTrees(snapshotIds: Collection<String>): Map<String, SnapshotNode> =
        withContext(dispatcher) {
            snapshotIds.mapNotNull { id -> nodeService[id]?.let { id to it } }.toMap()
        }

    /**
     * Walk [rootPath], hash every file, and persist both the snapshot header
     * and its node tree. Returns `null` if the path isn't a readable
     * directory (the dialog validator should already have blocked this, but
     * we re-check so the repo is safe to call from anywhere).
     *
     * The snapshot id is generated here rather than inside the DAO because
     * the current SQL DAO returns its input unchanged and discards the
     * [Snapshot.toSaved]-generated id.
     */
    suspend fun create(rootPath: String): CreatedSnapshot? = withContext(dispatcher) {
        val dir = File(rootPath)
        if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return@withContext null

        // Hashing every file in the tree — the reason we're on [dispatcher].
        val rawTree = TreeBuilder.buildTree(dir)

        val saved = SavedSnapshot(
            id = UUID.randomUUID().toString(),
            rootPath = dir.absolutePath,
            createdAt = Clock.System.now(),
        )
        snapshotService.create(saved) ?: return@withContext null

        val tree = rawTree.toDomain(saved.id)
        nodeService.create(tree)

        CreatedSnapshot(saved, tree)
    }

    /** Result of a successful [create]: both pieces the UI needs to render the new row. */
    data class CreatedSnapshot(val snapshot: SavedSnapshot, val tree: SnapshotNode)

    /**
     * Remove a snapshot and every node belonging to it. Returns `true` if the
     * snapshot header was deleted, `false` if the id wasn't present.
     *
     * Nodes are removed first so that if the snapshot delete somehow fails
     * we're left with an orphan header (harmless, user can retry) rather
     * than orphan nodes hanging off a deleted id.
     */
    suspend fun delete(snapshotId: String): Boolean = withContext(dispatcher) {
        val existing = snapshotService.getById(snapshotId) ?: return@withContext false
        nodeService.deleteBySnapshotId(snapshotId)
        snapshotService.delete(existing)
    }
}

/**
 * Template CRUD for the desktop UI. Same dispatcher contract as
 * [SnapshotRepository].
 *
 * The domain layer currently only implements [DirectoryTemplate], so
 * [create] always produces one regardless of the icon key passed from the
 * UI. Tags from the UI aren't yet represented in the domain model and are
 * dropped on write. The chosen icon is stored UI-side in [IconPreferences]
 * rather than the template SQL schema — it's purely cosmetic.
 */
class TemplateRepository(
    service: TemplateService? = null,
    private val iconPrefs: IconPreferences = IconPreferences(),
    private val relations: TemplateRelations = TemplateRelations(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val service: TemplateService by lazy { service ?: TemplateService() }

    suspend fun listAll(): List<Template> = withContext(dispatcher) { service.getAll() }

    /** All templates plus the icon ref the UI should draw for each one. */
    suspend fun listAllWithIcons(): List<Pair<Template, String>> = withContext(dispatcher) {
        val prefs = iconPrefs.getAll()
        service.getAll().map { it to (prefs[it.id.toString()] ?: "generic") }
    }

    /**
     * Full UI-facing template catalogue: the built-in packaged templates
     * first (so they always sit at the top), then the user's own. Each
     * user template carries its persisted icon + parent id + chosen
     * tool-version selections, so the detail pane doesn't have to round-
     * trip to the prefs layer.
     *
     * Version detection runs on the IO dispatcher via [VersionDetector]'s
     * cache — cheap after the first call.
     */
    suspend fun listAllForUi(): List<TemplateUiModel> = withContext(dispatcher) {
        val prefs = iconPrefs.getAll()
        val parents = relations.getAllParents()

        val packaged = PACKAGED_TEMPLATES.map { pkg ->
            val detected = pkg.tools.associateWith {
                org.open.file.ui.util.VersionDetector.detectVersions(it)
            }
            pkg.toUiModel(detected)
        }.map { p ->
            // Packaged templates pick up per-template user overrides too
            // (e.g. "this particular kotlin-gradle project targets 1.9.22").
            val selectedOverrides = relations.getSelectedVersions(p.id)
            p.copy(selectedToolVersions = p.selectedToolVersions + selectedOverrides)
        }

        val user = service.getAll().map { tmpl ->
            val id = tmpl.id.toString()
            tmpl.toUiModel(icon = prefs[id] ?: "generic")
                .copy(baseTemplateId = parents[id])
        }

        // User templates render first so someone's own work is
        // always at the top of the list — their fifteen custom
        // templates shouldn't be buried under seven built-ins they
        // already know about. Packaged rows stay in their
        // PACKAGED_TEMPLATES declaration order below.
        user + packaged
    }

    /**
     * Zero-detection form of [listAllForUi] — packaged templates come
     * back with empty `detectedToolVersions` maps. Intended for the
     * startup path: paint the UI immediately, then call
     * [refreshDetectedVersions] in the background to fill in tool
     * availability without blocking the first frame.
     *
     * Skipping the detection saves ~1–2s on a cold start where every
     * template shells out `<tool> --version` for 3–4 tools. That
     * latency was the single biggest startup stall.
     */
    suspend fun listAllForUiFast(): List<TemplateUiModel> = withContext(dispatcher) {
        val prefs = iconPrefs.getAll()
        val parents = relations.getAllParents()

        val packaged = PACKAGED_TEMPLATES.map { it.toUiModelFast() }.map { p ->
            val selectedOverrides = relations.getSelectedVersions(p.id)
            p.copy(selectedToolVersions = p.selectedToolVersions + selectedOverrides)
        }
        val user = service.getAll().map { tmpl ->
            val id = tmpl.id.toString()
            tmpl.toUiModel(icon = prefs[id] ?: "generic")
                .copy(baseTemplateId = parents[id])
        }
        // User templates first — see listAllForUi for rationale.
        user + packaged
    }

    /**
     * Run every tool's `--version` detection on this dispatcher, then
     * hand the caller a per-template map so they can patch their
     * Compose state list in-place. We return results rather than
     * mutating the list ourselves because the list is Compose-owned
     * (mutation must happen on the EDT); the caller resumes on its
     * own dispatcher after awaiting us.
     *
     * Detection is per-tool (not per-template) so shared tools like
     * `gradle` only run their shellout once across all templates. The
     * underlying [VersionDetector] already caches across calls, but
     * we de-dupe up front here so even a cold cache isn't asked for
     * the same tool twice in one pass.
     */
    suspend fun refreshDetectedVersions(
        templates: List<TemplateUiModel>,
    ): Map<String, Map<String, List<String>>> = withContext(dispatcher) {
        val allTools = templates.flatMap { it.tools }.toSet()
        val perTool: Map<String, List<String>> = allTools.associateWith {
            org.open.file.ui.util.VersionDetector.detectVersions(it)
        }
        templates.associate { t ->
            t.id to t.tools.associateWith { (perTool[it] ?: emptyList()) }
        }
    }

    suspend fun get(id: String): Template? = withContext(dispatcher) { service[id] }

    /**
     * Persist a new template and its UI-side icon preference.
     *
     * [icon] is either a built-in key (e.g. `"kotlin"`, `"generic"`) or an
     * absolute path to a user-uploaded image. In the path case we copy the
     * file into [IconPreferences]'s managed directory so a later move/delete
     * of the user's original file doesn't break the UI.
     */
    suspend fun create(
        name: String,
        description: String,
        rootDir: String,
        icon: String,
        /** UI tags. Not yet represented in the domain model. */
        @Suppress("UNUSED_PARAMETER") tags: List<String>,
        /**
         * Optional id of another template to inherit from. Stored as a
         * UI relation (not in the SQL schema) — see TemplateRelations.
         * Packaged ids are valid parents, so a user template can extend
         * e.g. `packaged:kotlin-gradle`.
         */
        baseTemplateId: String? = null,
    ): Pair<Template, String>? = withContext(dispatcher) {
        val template = DirectoryTemplate(
            file = File(rootDir),
            id = UUID.randomUUID(),
            name = name,
            description = description,
            config = DirectoryTemplateConfig(),
        )
        val saved = service.create(template) ?: return@withContext null
        baseTemplateId?.takeIf { it.isNotBlank() }?.let {
            relations.setParent(saved.id.toString(), it)
        }

        // Import uploaded images into our managed directory so the stored ref
        // stays stable even if the user later moves the source file. Built-in
        // keys just pass through.
        val storedIcon = try {
            if (iconPrefs.isFilePath(icon)) {
                iconPrefs.importUpload(File(icon))
            } else {
                icon
            }
        } catch (t: Throwable) {
            System.err.println("desktop-ui: icon import failed, falling back to generic: ${t.message}")
            "generic"
        }
        iconPrefs.set(saved.id.toString(), storedIcon)

        saved to storedIcon
    }

    suspend fun delete(id: String): Boolean = withContext(dispatcher) {
        // Guard packaged templates: they live in code, not the DB,
        // and the service would silently no-op on their synthetic ids
        // anyway — returning false here keeps the UI honest.
        if (isPackagedTemplateId(id)) return@withContext false

        // If the template's source lives inside our managed templates
        // directory (i.e. it was cloned from a git URL), wipe that
        // clone too. User-pointed sources (their own directories) are
        // left alone.
        val managedRoot = FileSystemUtils.home("templates").absoluteFile
        val existing = runCatching { service[id] }.getOrNull()
        val source = (existing?.target as? File)?.absoluteFile
        if (source != null && source.toPath().startsWith(managedRoot.toPath())) {
            runCatching { source.deleteRecursively() }
        }

        val ok = service.delete(id)
        // Always clear the icon pref — if the DB delete already succeeded on
        // a previous attempt, we still want the on-disk ref cleaned up.
        iconPrefs.remove(id)
        relations.removeAll(id)
        ok
    }

    /** Persist a user's version choice for [tool] on [templateId]. Packaged and user templates both accepted. */
    suspend fun setToolVersion(templateId: String, tool: String, version: String) = withContext(dispatcher) {
        relations.setSelectedVersion(templateId, tool, version)
    }

    /**
     * Clone [gitUrl] into the app's managed template directory
     * (`~/.open-file/templates/<id>/`) and persist a new DirectoryTemplate
     * pointing at the clone.
     *
     * On clone failure nothing is persisted — the destination directory is
     * removed and a null pair is returned so the caller can surface the
     * error via the reporter without inconsistent state landing in the DB.
     */
    suspend fun createFromGit(
        gitUrl: String,
        name: String,
        description: String,
        icon: String,
        tags: List<String>,
        baseTemplateId: String? = null,
        branch: String? = null,
    ): Pair<Template, String>? = withContext(dispatcher) {
        val id = UUID.randomUUID()
        val clonesRoot = FileSystemUtils.home("templates").apply { mkdirs() }
        val cloneDir = File(clonesRoot, id.toString())

        val result = GitCloner.clone(gitUrl, cloneDir, branch = branch)
        if (!result.success) {
            // Surface the git output via exception so the UI's catch
            // block routes it through the ErrorReporter toast.
            throw IllegalStateException(result.errorMessage ?: "git clone failed")
        }

        val template = DirectoryTemplate(
            file = cloneDir,
            id = id,
            name = name,
            description = description,
            config = DirectoryTemplateConfig(),
        )
        val saved = service.create(template) ?: run {
            // Roll back the clone if the DB insert fails.
            runCatching { cloneDir.deleteRecursively() }
            return@withContext null
        }
        baseTemplateId?.takeIf { it.isNotBlank() }?.let {
            relations.setParent(saved.id.toString(), it)
        }
        val storedIcon = try {
            if (iconPrefs.isFilePath(icon)) iconPrefs.importUpload(File(icon)) else icon
        } catch (_: Throwable) { "generic" }
        iconPrefs.set(saved.id.toString(), storedIcon)
        saved to storedIcon
    }

    /**
     * Materialise the template into [destination]. For user templates
     * we copy the on-disk source tree; for packaged templates we invoke
     * the template's own `scaffold(dest, selectedVersions)` function,
     * which writes a hand-authored starter layout. Returns a summary
     * the UI can show in a toast and/or pass to revealInFileExplorer.
     *
     * The destination is created if it doesn't exist. Existing files
     * are NOT overwritten — `copyRecursively(..., overwrite = false)`
     * throws on conflict, which the UI's catch surfaces as an error.
     */
    suspend fun scaffold(templateId: String, destination: File, projectName: String): ScaffoldResult? = withContext(dispatcher) {
        destination.parentFile?.mkdirs()

        // Packaged branch: generate in code. No DB read needed; the
        // template id uniquely identifies the packaged entry.
        PACKAGED_TEMPLATES.firstOrNull { it.id == templateId }?.let { pkg ->
            val selected = relations.getSelectedVersions(templateId)
            // Pre-wrap the output dir in a `<projectName>/` subfolder
            // when the template's own init command doesn't do it for
            // us. Most `<tool> init` commands (gradle, cargo init,
            // npm init, go mod init) write into their CWD without
            // creating a subdir — handed the user's raw output dir,
            // they'd splatter files directly into, say, ~/Documents.
            // `createsOwnTargetDir = true` is the opt-out for tools
            // like `npm create vite` that insist on creating the
            // target themselves.
            val scaffoldDir = if (pkg.createsOwnTargetDir) destination
                else File(destination, projectName)
            pkg.scaffold(scaffoldDir, selected, projectName)

            // The effective output — what we report to the caller so
            // revealInFileExplorer lands on the actual project dir
            // rather than its parent. For `createsOwnTargetDir`
            // templates we still want to return the project dir the
            // tool presumably created.
            val reportedDestination = if (pkg.createsOwnTargetDir)
                File(destination, projectName).absolutePath
                else scaffoldDir.absolutePath
            return@withContext ScaffoldResult(
                destination = reportedDestination,
                mode = ScaffoldMode.PACKAGED,
                sourceDescription = pkg.name,
            )
        }

        // User branch: resolve the template via the service, then
        // populate the project directory from whichever source shape
        // the template carries. Two shapes supported:
        //
        //   1. Directory — copyRecursively into the project dir.
        //      This is what "create template from existing folder" /
        //      "clone from git" produces.
        //   2. Zip file — extract via ArchiveExtractor, same pipeline
        //      the Restore Backup flow uses. Lets users ship a
        //      pre-packaged zip on disk as a custom template without
        //      us having to materialise it first.
        //
        // Both paths pre-wrap in `<output>/<projectName>/` so the
        // output shape matches packaged templates. Both paths run
        // the same `{{PROJECT_NAME}}` placeholder substitution that
        // zipScaffold applies, so a user template the user put
        // together from an existing project's tree can parametrise
        // its config files exactly like a packaged zip template can.
        //
        // Each null-return path logs its reason to stderr so a user
        // seeing "Scaffold failed: template or source missing" can
        // match the toast against a concrete cause in the log file.
        val tmpl = service[templateId] ?: run {
            System.err.println("scaffold: template not found by id=$templateId")
            return@withContext null
        }
        val source = (tmpl.target as? File) ?: run {
            System.err.println(
                "scaffold: template $templateId target is not a File " +
                    "(got ${tmpl.target?.javaClass?.name ?: "null"})"
            )
            return@withContext null
        }
        if (!source.exists()) {
            System.err.println("scaffold: source path does not exist: ${source.absolutePath}")
            return@withContext null
        }

        val projectDir = File(destination, projectName)
        projectDir.mkdirs()

        when {
            source.isDirectory -> {
                // Existing behaviour — full recursive copy of the
                // template tree. `onError = SKIP` so a single
                // unreadable file (permissions quirk, broken
                // symlink) doesn't abort the whole scaffold; the
                // rest of the tree still lands and the user can
                // patch up the hole after.
                source.copyRecursively(
                    target = projectDir,
                    overwrite = false,
                    onError = { f, e ->
                        System.err.println("scaffold: skip ${f.absolutePath}: ${e.message}")
                        OnErrorAction.SKIP
                    },
                )
            }
            source.isFile && source.extension.equals("zip", ignoreCase = true) -> {
                // New — zip source. ArchiveExtractor handles the
                // zip-slip guards + entry-path hygiene, so we don't
                // rebuild that logic per surface.
                org.open.file.archive.ArchiveExtractor.extract(
                    archive = source,
                    destination = projectDir,
                    format = org.open.file.archive.ArchiveFormat.ZIP,
                )
            }
            else -> {
                System.err.println(
                    "scaffold: source ${source.absolutePath} is neither a directory " +
                        "nor a .zip — template is misconfigured"
                )
                return@withContext null
            }
        }

        // Same placeholder rewrite the packaged zipScaffold helper
        // does. Running it unconditionally means user templates get
        // the {{PROJECT_NAME}} behaviour for free — they can bake
        // the token into their template tree the same way packaged
        // zips do.
        applyTemplatePlaceholders(projectDir, projectName)

        ScaffoldResult(
            destination = projectDir.absolutePath,
            mode = ScaffoldMode.COPY,
            sourceDescription = source.absolutePath,
        )
    }

    enum class ScaffoldMode { PACKAGED, COPY }
    data class ScaffoldResult(
        val destination: String,
        val mode: ScaffoldMode,
        val sourceDescription: String,
    )
}

/**
 * Backup CRUD + archive creation for the desktop UI.
 *
 * Same dispatcher contract as [SnapshotRepository] / [TemplateRepository]:
 * every public call is a `suspend` that bounces off the EDT so the UI
 * thread never blocks on zip writes or SQLite.
 *
 * The archiver itself (`BackupArchiver`) lives in `:shared:backup` and
 * reuses `:shared:snapshot`'s `TreeBuilder` — every backup carries a
 * hashed tree of exactly what got archived, not a separate re-scan. The
 * repo also records that tree as a real snapshot when requested so
 * "Backups" and "Snapshots" stay consistent (same id referenced from both
 * tables).
 */
class BackupRepository(
    backupService: BackupService? = null,
    snapshotService: SnapshotService? = null,
    nodeService: NodeService? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val backupService: BackupService by lazy { backupService ?: BackupService() }
    private val snapshotService: SnapshotService by lazy { snapshotService ?: SnapshotService() }
    private val nodeService: NodeService by lazy { nodeService ?: NodeService() }

    /** Default parent directory for archives the user doesn't explicitly place. */
    private val defaultArchiveDir: File by lazy {
        FileSystemUtils.home("backups").apply { mkdirs() }
    }

    suspend fun listAll(): List<SavedBackup> = withContext(dispatcher) {
        backupService.getAll().map { (it as? SavedBackup) ?: it.toSaved() }
    }

    /**
     * Build a compressed archive of [sourcePath] and persist a backup row.
     *
     * Destination resolution, in priority order:
     *  1. [destinationPath] — full archive file path, if the caller
     *     already picked a name.
     *  2. [targetDirectory] — parent directory; filename is auto-chosen
     *     as `<dirname>-<timestamp>.zip` inside it. This is the common
     *     "user picked where to put the zip" case.
     *  3. Neither — we place the archive under
     *     `~/.open-file/backups/<dirname>-<timestamp>.zip` so one-click
     *     backups from the UI just work.
     *
     * The resolved parent directory is stamped onto the resulting
     * [SavedBackup] as its `targetDirectory`, so the row carries the
     * user's placement choice forward to future scheduled / replay runs.
     *
     * When [alsoRecordSnapshot] is true the tree walked by the archiver is
     * also written into the snapshots DB with its id linked back on the
     * backup row — letting the user jump from a backup's detail panel to
     * the snapshot it captures.
     *
     * [onProgress] fires on whichever thread the IO dispatcher picks, once
     * per scanned and once per compressed file. The UI is expected to
     * marshal state updates back onto the EDT itself (Compose's snapshot
     * state handles this automatically when reached inside a `snapshot`
     * block or via plain assignment from any thread).
     *
     * Cancellation is cooperative: when the caller's coroutine job is
     * cancelled, the wrapped `withContext` block sees `!isActive`, we
     * forward that into the archiver, and the archiver throws
     * [kotlin.coroutines.cancellation.CancellationException] on the next
     * poll. The partial archive is deleted inside the archiver.
     */
    suspend fun create(
        sourcePath: String,
        destinationPath: String? = null,
        targetDirectory: String? = null,
        compression: CompressionType = CompressionType.ZIP,
        alsoRecordSnapshot: Boolean = true,
        includeHidden: Boolean = true,
        onProgress: (BackupArchiver.Progress) -> Unit = {},
    ): SavedBackup? = withContext(dispatcher) {
        val source = File(sourcePath)
        if (!source.exists() || !source.isDirectory || !source.canRead()) return@withContext null

        val parentDir: File = when {
            destinationPath != null -> File(destinationPath).parentFile ?: defaultArchiveDir
            targetDirectory != null -> File(targetDirectory).apply { mkdirs() }
            else -> defaultArchiveDir
        }
        val destination = destinationPath?.let { File(it) }
            ?: defaultArchiveFileFor(source, compression, parentDir)

        // `this` inside withContext is the CoroutineScope carrying the
        // active Job. Capture its isActive flag so the archiver — which is
        // plain blocking code with no suspension points of its own — can
        // poll it between files and bail quickly when the UI cancels.
        val scope = this
        val result = BackupArchiver.archive(
            source = source,
            destination = destination,
            compression = compression,
            onProgress = onProgress,
            isCancelled = { !scope.isActive },
            includeHidden = includeHidden,
        )
        val now = Clock.System.now()

        // Optionally persist a snapshot alongside the backup. We write the
        // snapshot first so a failure here doesn't leave a backup row
        // pointing at a non-existent snapshotId.
        val snapshotId = if (alsoRecordSnapshot) {
            val snap = SavedSnapshot(
                id = UUID.randomUUID().toString(),
                rootPath = source.absolutePath,
                createdAt = now,
            )
            val created = snapshotService.create(snap)
            if (created != null) {
                val tree = result.tree.toDomain(snap.id)
                nodeService.create(tree)
                snap.id
            } else null
        } else null

        val backup = result.backup.copy(
            createdAt = now,
            snapshotId = snapshotId,
            // Re-stamp the target with the resolved parent in case the
            // archiver fell back (e.g. defaultArchiveFileFor decided) —
            // we want the DB row to match where the file actually landed.
            targetDirectory = destination.parentFile?.absolutePath,
            // Re-stamp includeHidden too — data-class `.copy` carries
            // the field through automatically because it's already on
            // UnsavedBackup, but being explicit here keeps the list
            // of "persistence-critical" fields visible in one place.
            includeHidden = includeHidden,
        )
        val persisted = backupService.create(backup) ?: return@withContext null
        (persisted as? SavedBackup) ?: persisted.toSaved()
    }

    /**
     * Remove a backup's DB row, its on-disk archive, and (if present) the
     * snapshot it linked to. The archive file delete is best-effort — a
     * user could have moved / removed it themselves, and that shouldn't
     * block us from cleaning up the metadata.
     */
    suspend fun delete(id: String): Boolean = withContext(dispatcher) {
        val existing = backupService.getById(id) ?: return@withContext false
        val saved = (existing as? SavedBackup) ?: existing.toSaved(id)

        // Nuke the archive file first — orphaned files are worse than
        // orphaned rows for disk-space reasons.
        runCatching { File(saved.destinationPath).takeIf { it.exists() }?.delete() }

        // Clean up the linked snapshot (nodes cascade via SnapshotRepository's
        // delete path, but we mirror that here since we don't want a repo-
        // to-repo dependency).
        saved.snapshotId?.let { sid ->
            runCatching { nodeService.deleteBySnapshotId(sid) }
            runCatching { snapshotService.getById(sid)?.let { snapshotService.delete(it) } }
        }

        backupService.deleteById(id)
    }

    private fun defaultArchiveFileFor(
        source: File,
        compression: CompressionType,
        parentDir: File = defaultArchiveDir,
    ): File {
        val stamp = System.currentTimeMillis()
        val base = source.name.ifBlank { "backup" }
        return File(parentDir, "$base-$stamp.${compression.extension}")
    }

    /**
     * Unpack a backup's archive into [destinationPath]. Returns a summary
     * of what was restored, or null if the backup id is unknown or its
     * archive file is missing from disk.
     *
     * The destination is created if it doesn't exist. Existing files with
     * overlapping paths are overwritten — the UI is expected to confirm
     * with the user before calling.
     */
    suspend fun restore(backupId: String, destinationPath: String): RestoreResult? = withContext(dispatcher) {
        val existing = backupService.getById(backupId) ?: return@withContext null
        val saved = (existing as? SavedBackup) ?: existing.toSaved(backupId)
        val archive = File(saved.destinationPath)
        if (!archive.exists() || !archive.canRead()) return@withContext null

        val dest = File(destinationPath)
        // Map the backup-domain CompressionType to the generic
        // ArchiveFormat consumed by :shared:archive. One enum value
        // each today (ZIP); the mapping exists so adding a new
        // compression to backups doesn't require a matching change
        // in the archive module and vice versa.
        val format = when (saved.compression) {
            CompressionType.ZIP -> ArchiveFormat.ZIP
        }
        val result = ArchiveExtractor.extract(archive, dest, format)
        RestoreResult(
            destination = dest.absolutePath,
            fileCount = result.fileCount,
            directoryCount = result.directoryCount,
            totalBytes = result.totalBytes,
            skipped = result.skipped,
        )
    }

    /** What the UI needs after a successful restore — destination + summary counts. */
    data class RestoreResult(
        val destination: String,
        val fileCount: Int,
        val directoryCount: Int,
        val totalBytes: Long,
        /**
         * Entries the extractor couldn't write — typically read-only
         * files under `.git/objects/` on Windows or files another
         * process held open. Non-empty means the restore succeeded
         * partially; the UI should warn the user and list the
         * casualties so they can re-run the failing archives
         * individually if needed.
         */
        val skipped: List<org.open.file.archive.ArchiveExtractor.SkippedEntry> = emptyList(),
    )
}

// ──────────────────────────────────────────────
// Domain → UI adapter
// ──────────────────────────────────────────────

private val templateDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

// ──────────────────────────────────────────────
// Backup UI adapter
// ──────────────────────────────────────────────

/**
 * Format a raw byte count the same way the snapshot list formats sizes —
 * `B`, `KB`, `MB`, `GB` — so backups and snapshots use consistent units.
 */
private fun formatByteCount(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}

/**
 * Turn a persisted [SavedBackup] into the UI model rendered by
 * [org.open.file.ui.screens.BackupsScreen].
 *
 *  - `name` is derived from the source directory's last path segment, so the
 *    list reads like "my-project" instead of "/Users/alice/code/my-project".
 *  - `size` carries the archived size, which is what the user cares about for
 *    disk-space decisions; the pre-compression figure is in `originalSize`.
 *  - `snapshotIds` is always 0 or 1 entries today (every backup optionally
 *    records one snapshot), but the field is plural in anticipation of
 *    multi-snapshot bundles.
 *  - `status` is always COMPLETED — we only persist a row after archiving
 *    succeeds. RUNNING / FAILED states belong to an async job tracker we
 *    haven't built yet.
 */
fun SavedBackup.toUiModel(): BackupUiModel = BackupUiModel(
    id = id,
    name = File(rootPath).name.ifBlank { rootPath },
    // Preserve the source path for the detail pane's history view —
    // backups that share a rootPath get grouped into a chronological
    // timeline so the user can see where the current one sits.
    rootPath = rootPath,
    snapshotIds = snapshotId?.let { listOf(it) } ?: emptyList(),
    destination = destinationPath,
    // Fall back to the archive file's parent when the row was
    // written before the column existed, so older backups still
    // render a target directory in the detail pane.
    targetDirectory = targetDirectory ?: File(destinationPath).parent,
    createdAt = createdAt,
    size = formatByteCount(archivedSize),
    status = BackupStatus.COMPLETED,
    compression = compression.name.lowercase(),
    // Raw values exposed alongside the formatted `size` so the
    // identical-backup pre-check in Main.kt can compare exactly.
    originalSizeBytes = originalSize,
    entryCount = entryCount,
    // Surfaced so the "new backup from same source" replay can
    // match what the user picked originally rather than silently
    // defaulting to true.
    includeHidden = includeHidden,
)

/**
 * Flatten a domain [Template] into the [TemplateUiModel] the screens render.
 * Fields the domain layer doesn't yet carry (tags, config, preview tree) are
 * surfaced as empty values for now so the adapter can stay total. [icon] is
 * injected by the caller from [IconPreferences] since it lives UI-side.
 */
fun Template.toUiModel(icon: String = "generic"): TemplateUiModel {
    // DirectoryTemplate.target is a File; other target types (none today)
    // won't expose a source path. Keep the cast defensive so future
    // template kinds don't break the adapter.
    val source = (target as? java.io.File)?.absolutePath
    return TemplateUiModel(
        id = id.toString(),
        type = type,
        name = name,
        description = description,
        tags = emptyList(),
        created = templateDateFormat.format(created),
        updated = templateDateFormat.format(updated),
        config = emptyMap(),
        previewTree = null,
        icon = icon,
        sourcePath = source,
    )
}
