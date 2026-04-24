package backup.cli

import backup.App
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Options
import org.open.file.cli.CommandLineHandler
import org.open.file.cli.delete
import org.open.file.cli.id
import org.open.file.cli.list
import org.open.file.cli.models.DefaultCommandState
import org.open.file.cli.models.DefaultCommandState.Companion.fromCommandState
import org.open.file.cli.new
import org.open.file.cli.path
import org.open.file.cli.restore
import org.open.file.cli.target

/**
 * Options for `openfile backup <verb>`:
 *   -ls / --list                          list every backup
 *   -n  / --new     --path <dir>
 *                   [--target <dir>]
 *                   [--no-hidden]         archive <dir>
 *   -r  / --restore --id <uuid>
 *                   --path <destDir>      extract into destDir
 *   -d  / --delete  --id <uuid>           delete row + archive
 */
class RootCommandLineHandler : CommandLineHandler<App>() {

    override val utilityName: String get() = "Backups"

    override val options: Options get() = Options().apply {
        addOption("n", "new", false, "Create a new backup of --path.")
        addOption("ls", "list", false, "List all backups.")
        addOption("r", "restore", false, "Restore a backup (--id) into --path.")
        addOption("d", "delete", false, "Delete a backup by --id.")
        addOption("p", "path", true, "Source dir (for --new) or destination dir (for --restore).")
        addOption(null, "target", true, "Optional target directory for the archive file (--new).")
        addOption(null, "no-hidden", false, "Exclude dotfiles / dotdirs from the archive (--new).")
        addOption("i", "id", true, "Backup id (for --restore / --delete).")
        addOption("h", "help", false, "Shows the help menu.")
        addOption("q", "quit", false, "Quits the application.")
    }

    override fun handle(cmd: CommandLine): DefaultCommandState {
        val base = super.handle(cmd)
        if (base.success) return fromCommandState(base)

        val app = App()

        if (cmd.list) {
            return DefaultCommandState(true, app.list())
        }

        if (cmd.new) {
            val path = cmd.path
            if (path.isNullOrBlank()) {
                logger.error("--new requires --path <dir>")
                return DefaultCommandState(false)
            }
            val saved = app.create(
                sourcePath = path,
                targetDir = cmd.target,
                includeHidden = !cmd.hasOption("no-hidden"),
            )
            return if (saved != null) {
                DefaultCommandState(true, saved).apply { mutation = true }
            } else {
                DefaultCommandState(false).apply { mutation = true }
            }
        }

        if (cmd.restore) {
            val id = cmd.id
            val path = cmd.path
            if (id.isNullOrBlank() || path.isNullOrBlank()) {
                logger.error("--restore requires --id <uuid> and --path <destDir>")
                return DefaultCommandState(false)
            }
            return DefaultCommandState(app.restore(id, path), id)
        }

        if (cmd.delete) {
            val id = cmd.id
            if (id.isNullOrBlank()) {
                logger.error("--delete requires --id <uuid>")
                return DefaultCommandState(false)
            }
            return DefaultCommandState(app.delete(id), id).apply { mutation = true }
        }

        return DefaultCommandState(false)
    }
}
