package snapshot.cli

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
import snapshot.App

/**
 * Options for `openfile snapshot <verb>`:
 *   -ls / --list                   list every snapshot
 *   -n  / --new    --path <dir>    snapshot <dir>
 *   -d  / --delete --id <uuid>     delete snapshot by id
 */
class RootCommandLineHandler : CommandLineHandler<App>() {

    override val utilityName: String get() = "Snapshots"

    override val options: Options get() = Options().apply {
        addOption("n", "new", false, "Create a new snapshot of --path.")
        addOption("ls", "list", false, "List all snapshots.")
        addOption("d", "delete", false, "Delete a snapshot by --id.")
        addOption("p", "path", true, "Root directory to snapshot (for --new).")
        addOption("i", "id", true, "Snapshot id (for --delete).")
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
            val saved = app.create(path)
            return if (saved != null) {
                DefaultCommandState(true, saved).apply { mutation = true }
            } else {
                DefaultCommandState(false).apply { mutation = true }
            }
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
